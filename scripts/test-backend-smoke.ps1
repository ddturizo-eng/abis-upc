param(
    [string]$BaseUrl = "http://localhost:7000",
    [string]$Usuario = $env:ABIS_ADMIN_USER,
    [string]$Password = $env:ABIS_ADMIN_PASSWORD,
    [string]$Identificacion = $env:ABIS_TEST_IDENTIFICACION,
    [long]$IdEleccion = 0,
    [long]$IdCandidato = 0,
    [long]$IdPuesto = 0,
    [long]$IdMesa = 0,
    [string]$CargoJurado = "VOCAL",
    [switch]$RunMutating
)

$ErrorActionPreference = "Stop"

function Invoke-AbisJson {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null,
        [int[]]$ExpectedStatus = @(200)
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $uri = "$BaseUrl$Path"
    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = $Body | ConvertTo-Json -Depth 8
    }

    try {
        $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -Body $jsonBody -ContentType "application/json"
        $status = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        if ($_.Exception.Response -eq $null) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()
    }

    if ($ExpectedStatus -notcontains $status) {
        throw "[$Method $Path] estado HTTP $status; esperado $($ExpectedStatus -join ', '). Respuesta: $content"
    }

    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($content)) {
        try {
            $parsed = $content | ConvertFrom-Json
        } catch {
            $parsed = $content
        }
    }

    [pscustomobject]@{
        Method = $Method
        Path = $Path
        Status = $status
        Body = $parsed
    }
}

function Assert-Value {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

Write-Host "ABIS-UPC backend smoke tests"
Write-Host "BaseUrl: $BaseUrl"

$status = Invoke-AbisJson -Method GET -Path "/api/status" -ExpectedStatus @(200, 503)
Write-Host "[OK] /api/status => HTTP $($status.Status)"

$puestos = Invoke-AbisJson -Method GET -Path "/api/puestos"
Write-Host "[OK] /api/puestos => HTTP $($puestos.Status)"

Assert-Value -Condition (-not [string]::IsNullOrWhiteSpace($Usuario)) -Message "Falta Usuario. Usa -Usuario o ABIS_ADMIN_USER."
Assert-Value -Condition (-not [string]::IsNullOrWhiteSpace($Password)) -Message "Falta Password. Usa -Password o ABIS_ADMIN_PASSWORD."

$login = Invoke-AbisJson -Method POST -Path "/api/auth/login" -Body @{
    usuario = $Usuario
    password = $Password
}

$token = $login.Body.token
Assert-Value -Condition (-not [string]::IsNullOrWhiteSpace($token)) -Message "Login no retorno token. Respuesta: $($login.Body | ConvertTo-Json -Depth 5)"
Write-Host "[OK] /api/auth/login => token recibido"

$dashboard = Invoke-AbisJson -Method GET -Path "/api/admin/dashboard" -Token $token
Write-Host "[OK] /api/admin/dashboard => auth valida"

$oldClose = Invoke-AbisJson -Method POST -Path "/api/elecciones/1/cerrar" -Token $token -ExpectedStatus @(404, 405)
Write-Host "[OK] POST /api/elecciones/{id}/cerrar bloqueado => HTTP $($oldClose.Status)"

if ($IdEleccion -gt 0) {
    $resultados = Invoke-AbisJson -Method GET -Path "/api/elecciones/$IdEleccion/resultados" -Token $token
    Write-Host "[OK] /api/elecciones/$IdEleccion/resultados => HTTP $($resultados.Status)"
}

if (-not [string]::IsNullOrWhiteSpace($Identificacion) -and $IdEleccion -gt 0) {
    $puedeVotar = Invoke-AbisJson -Method GET -Path "/api/votantes/$Identificacion/puede-votar?idEleccion=$IdEleccion" -Token $token
    Write-Host "[OK] /api/votantes/$Identificacion/puede-votar => HTTP $($puedeVotar.Status)"
}

if (-not $RunMutating) {
    Write-Host "[OK] Pruebas no destructivas finalizadas. Usa -RunMutating para probar procedimientos que modifican datos."
    exit 0
}

Assert-Value -Condition (-not [string]::IsNullOrWhiteSpace($Identificacion)) -Message "Para -RunMutating falta -Identificacion o ABIS_TEST_IDENTIFICACION."
Assert-Value -Condition ($IdEleccion -gt 0) -Message "Para -RunMutating falta -IdEleccion."
Assert-Value -Condition ($IdPuesto -gt 0) -Message "Para -RunMutating falta -IdPuesto."

$inhabilitar = Invoke-AbisJson -Method PUT -Path "/api/votantes/$Identificacion/inhabilitar" -Token $token -Body @{
    motivo = "Prueba automatizada smoke test"
} -ExpectedStatus @(200, 400, 403, 409)
Write-Host "[OK] /api/votantes/$Identificacion/inhabilitar => HTTP $($inhabilitar.Status)"

$habilitar = Invoke-AbisJson -Method PUT -Path "/api/votantes/$Identificacion/habilitar" -Token $token -Body @{
    motivo = "Reversion prueba automatizada smoke test"
} -ExpectedStatus @(200, 400, 403, 409)
Write-Host "[OK] /api/votantes/$Identificacion/habilitar => HTTP $($habilitar.Status)"

if ($IdMesa -gt 0) {
    $jurado = Invoke-AbisJson -Method POST -Path "/api/jurados/asignar" -Token $token -Body @{
        idMesa = $IdMesa
        identificacion = $Identificacion
        cargo = $CargoJurado
    } -ExpectedStatus @(200, 201, 400, 403, 409)
    Write-Host "[OK] /api/jurados/asignar => HTTP $($jurado.Status)"
}

$bodyVoto = @{
    identificacion = $Identificacion
    idEleccion = $IdEleccion
    idPuesto = $IdPuesto
    idCandidato = $null
}
if ($IdCandidato -gt 0) {
    $bodyVoto.idCandidato = $IdCandidato
}

$voto = Invoke-AbisJson -Method POST -Path "/api/votos/registrar" -Token $token -Body $bodyVoto -ExpectedStatus @(201, 400, 403, 409)
Write-Host "[OK] /api/votos/registrar => HTTP $($voto.Status)"

Write-Host "[OK] Smoke tests finalizados."
