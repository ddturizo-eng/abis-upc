@echo off
chcp 65001 >nul
title ABIS-UPC - Iniciar Servicios

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ADVERTENCIA] No se detectaron privilegios de administrador.
    echo     Algunas acciones como iniciar DpHost o cerrar procesos previos pueden fallar.
    echo     El script continuara de todos modos.
    echo.
)

echo ==========================================
echo   ABIS-UPC - Inicio de Servicios
echo ==========================================
echo.

set "ABIS_ROOT=C:\PROYECTOS P3\abis-upc"
set "ABIS_ENV_FILE=%ABIS_ROOT%\.env"

if exist "%ABIS_ENV_FILE%" (
    echo Cargando variables desde %ABIS_ENV_FILE%
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ABIS_ENV_FILE%") do (
        if not "%%A"=="" set "%%A=%%B"
    )
) else (
    echo ADVERTENCIA: No se encontro %ABIS_ENV_FILE%
    echo     Usando valores por defecto no sensibles.
)

if not defined ABIS_DB_URL set "ABIS_DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1"
if not defined ABIS_DB_USER set "ABIS_DB_USER=abisAdmin"
if not defined BIOMETRIC_SERVICE_URL set "BIOMETRIC_SERVICE_URL=http://localhost:8001"
if not defined OCR_SERVICE_URL set "OCR_SERVICE_URL=http://localhost:8002"
if not defined NATIVE_SERVICE_URL set "NATIVE_SERVICE_URL=http://localhost:8765"
if not defined ABIS_EMAIL_SERVICE_URL set "ABIS_EMAIL_SERVICE_URL=http://localhost:8010"
if not defined ABIS_JAVA_URL set "ABIS_JAVA_URL=http://localhost:7000"
if not defined SCANNER_PORT set "SCANNER_PORT=COM9"
if not defined SCANNER_BAUD set "SCANNER_BAUD=9600"
if not defined SCANNER_ID set "SCANNER_ID=YHD9601D-01"

echo [1/9] Verificando Oracle XE...
echo     Oracle se mantiene corriendo (no se toca)
echo.

echo     Liberando puertos ABIS anteriores...
for %%p in (7000 8001 8002 8765 8010) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%%p ^| findstr LISTENING') do (
        taskkill /F /PID %%a >nul 2>&1
    )
)
echo     Puertos ABIS listos
echo.

echo [2/9] Iniciando NativeService (huella digital, puerto 8765)...
net start DpHost >nul 2>&1
if %errorLevel% == 0 (
    echo     OK - DpHost iniciado
) else (
    echo     OK - DpHost ya estaba corriendo o no existe
)
cd /d "C:\PROYECTOS P3\abis-upc\abis-native\NativeService"
if exist "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48\NativeService.exe" (
    start "ABIS-NativeService" /min "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48\NativeService.exe"
    timeout /t 3 /nobreak >nul
    echo     NativeService iniciado en puerto 8765
) else if exist "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48-patched\NativeService.exe" (
    start "ABIS-NativeService" /min "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48-patched\NativeService.exe"
    timeout /t 3 /nobreak >nul
    echo     NativeService iniciado en puerto 8765
) else (
    echo     ADVERTENCIA: NativeService.exe no encontrado en la ruta esperada
)
echo.

echo [3/9] Verificando/instalando dependencias biometricas...
pip install python-dotenv httpx pymupdf --quiet 2>nul
echo     Dependencias biometricas OK
echo.

echo [4/9] Iniciando Servicio Biometrico (puerto 8001)...
cd /d "C:\PROYECTOS P3\abis-upc\abis-biometric"
start "ABIS-Biometric" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:ABIS_DB_URL='%ABIS_DB_URL%'; $env:ABIS_DB_USER='%ABIS_DB_USER%'; $env:ABIS_DB_PASSWORD='%ABIS_DB_PASSWORD%'; $env:NATIVE_SERVICE_URL='%NATIVE_SERVICE_URL%'; Set-Location 'C:\PROYECTOS P3\abis-upc\abis-biometric'; if (Test-Path '.\venv\Scripts\Activate.ps1') { . '.\venv\Scripts\Activate.ps1' }; python -m uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload"
echo     Biometrico iniciado en puerto 8001
echo.

echo [5/9] Verificando/instalando dependencias OCR...
cd /d "C:\PROYECTOS P3\abis-upc\abis-ocr"
pip install -r requirements.txt --quiet 2>nul || echo     Dependencias OCR con advertencias, continuando...
echo.

echo [6/9] Iniciando Servicio OCR (puerto 8002)...
if exist "api\main.py" (
    start "ABIS-OCR" powershell -NoExit -ExecutionPolicy Bypass -Command "Set-Location 'C:\PROYECTOS P3\abis-upc\abis-ocr'; if (Test-Path '.\venv\Scripts\Activate.ps1') { . '.\venv\Scripts\Activate.ps1' }; python -m uvicorn api.main:app --host 0.0.0.0 --port 8002"
    echo     OCR iniciado en puerto 8002
) else (
    echo     ADVERTENCIA: No se encontro api/main.py en abis-ocr
)
echo.

echo [7/9] Iniciando Email Service (puerto 8010)...
cd /d "C:\PROYECTOS P3\abis-upc\abis-email-service"
if exist "package.json" (
    start "ABIS-EmailService" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:PORT='8010'; $env:ABIS_EMAIL_SERVICE_TOKEN='%ABIS_EMAIL_SERVICE_TOKEN%'; $env:ABIS_RESEND_API_KEY='%ABIS_RESEND_API_KEY%'; $env:ABIS_RESEND_FROM_EMAIL='%ABIS_RESEND_FROM_EMAIL%'; Set-Location 'C:\PROYECTOS P3\abis-upc\abis-email-service'; npm start"
    echo     Email Service iniciado en puerto 8010
) else (
    echo     ADVERTENCIA: No se encontro abis-email-service/package.json
)
echo.

echo [8/9] Iniciando Backend Java (puerto 7000)...
cd /d "C:\PROYECTOS P3\abis-upc\abis-backend"
echo     Compilando backend para incluir rutas recientes...
call mvn -q -DskipTests package
if %errorlevel% neq 0 (
    echo     ERROR: No se pudo compilar el backend Java.
    echo     Revisa la salida de Maven antes de continuar.
    pause
    exit /b 1
)
cd /d "C:\PROYECTOS P3\abis-upc\abis-backend\target"
if not exist "abis-backend-1.0-SNAPSHOT.jar" (
    echo     ERROR: No se encontro abis-backend-1.0-SNAPSHOT.jar
    echo     Compila el proyecto primero: cd C:\PROYECTOS P3\abis-upc\abis-backend ^&^& mvn package
    pause
    exit /b 1
)
start "ABIS-Backend" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:ABIS_DB_URL='%ABIS_DB_URL%'; $env:ABIS_DB_USER='%ABIS_DB_USER%'; $env:ABIS_DB_PASSWORD='%ABIS_DB_PASSWORD%'; $env:BIOMETRIC_SERVICE_URL='%BIOMETRIC_SERVICE_URL%'; $env:OCR_SERVICE_URL='%OCR_SERVICE_URL%'; $env:NATIVE_SERVICE_URL='%NATIVE_SERVICE_URL%'; $env:ABIS_EMAIL_SERVICE_URL='%ABIS_EMAIL_SERVICE_URL%'; $env:ABIS_EMAIL_SERVICE_TOKEN='%ABIS_EMAIL_SERVICE_TOKEN%'; Set-Location 'C:\PROYECTOS P3\abis-upc\abis-backend\target'; java -jar abis-backend-1.0-SNAPSHOT.jar"
echo     Backend Java iniciado en puerto 7000
echo.

echo [9/9] Iniciando Scanner de Contingencia (puerto %SCANNER_PORT%)...
cd /d "%ABIS_ROOT%"
python -m pip install pyserial requests python-dotenv --quiet 2>nul
start "ABIS-ScannerContingencia" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:SCANNER_PORT='%SCANNER_PORT%'; $env:SCANNER_BAUD='%SCANNER_BAUD%'; $env:ABIS_JAVA_URL='%ABIS_JAVA_URL%'; $env:SCANNER_ID='%SCANNER_ID%'; $env:SCANNER_PUESTO_ID='%SCANNER_PUESTO_ID%'; Set-Location '%ABIS_ROOT%'; python scripts\contingencia_scanner.py --port '%SCANNER_PORT%' --baud %SCANNER_BAUD% --java-url '%ABIS_JAVA_URL%' --scanner-id '%SCANNER_ID%'"
echo     Scanner de contingencia iniciado
echo.

echo ==========================================
echo   Servicios iniciados
echo ==========================================
echo.
echo Verificacion:
echo   Frontend:  http://localhost:7000/
echo   Backend:   http://localhost:7000/api/status
echo   Biometr.:  http://localhost:8001/status
echo   OCR:       http://localhost:8002/health
echo   NativeSvc: http://localhost:8765
echo   EmailSvc:  http://localhost:8010/health
echo   Scanner:   %SCANNER_PORT% (%SCANNER_ID%)
echo.
echo Abriendo navegador con el frontend...
echo     Esperando respuesta del backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command "for ($i = 0; $i -lt 20; $i++) { try { Invoke-WebRequest -UseBasicParsing 'http://localhost:7000/api/status' -TimeoutSec 1 | Out-Null; exit 0 } catch { Start-Sleep -Seconds 1 } }; exit 1"
if %errorlevel% neq 0 (
echo     ADVERTENCIA: Backend no respondio aun; revisa la ventana ABIS-Backend
)
start "" "http://localhost:7000/pages/auth/login.html"
echo.
echo Presiona cualquier tecla para verificar servicios...
pause >nul

echo.
echo Verificando servicios...
curl -s http://localhost:7000/api/status >nul 2>&1 && echo   [OK] Backend (7000) || echo   [FAIL] Backend (7000)
curl -s http://localhost:8001/status >nul 2>&1 && echo   [OK] Biometr. (8001) || echo   [FAIL] Biometr. (8001)
curl -s http://localhost:8002/health >nul 2>&1 && echo   [OK] OCR (8002) || echo   [FAIL] OCR (8002)
curl -s http://localhost:8765/status >nul 2>&1 && echo   [OK] NativeService (8765) || echo   [FAIL] NativeService (8765)
curl -s http://localhost:8010/health >nul 2>&1 && echo   [OK] EmailSvc (8010) || echo   [FAIL] EmailSvc (8010)
echo   [INFO] Scanner serial: revisar ventana ABIS-ScannerContingencia
echo.
echo Listo. Usa DETENER_SERVICIOS.bat para detener.
echo.
pause
