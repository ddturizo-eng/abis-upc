# ABIS-UPC — Guía de Demo

## Orden de arranque (hacer ANTES de la demo)

### 1. Servicio DpHost (driver biométrico) — PowerShell Admin 
```
Start-Process -FilePath "C:\Program Files\DigitalPersona\Bin\DpHost.exe" -Verb RunAs
```

### 2. Oracle XE (si no está corriendo)
```
sqlplus / as sysdba
STARTUP;
EXIT;
```

### 3. Backend Java Javalin (puerto 7000) — PowerShell
```powershell
$env:ABIS_DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:ABIS_DB_USER="abisAdmin"
$env:ABIS_DB_PASSWORD="12345"
$env:BIOMETRIC_SERVICE_URL="http://localhost:8001"
$env:OCR_SERVICE_URL="http://localhost:8002"
java -jar "C:\PROYECTOS P3\abis-upc\abis-backend\target\abis-backend-1.0-SNAPSHOT.jar"
```

### 4. FastAPI Biométrico (puerto 8001) — Nueva terminal
```powershell
cd "C:\PROYECTOS P3\abis-upc\abis-biometric"
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

### 5. FastAPI OCR (puerto 8002) — Nueva terminal
```powershell
cd "C:\PROYECTOS P3\abis-upc\abis-ocr"
pip install -r requirements.txt
python -m uvicorn api.main:app --host 0.0.0.0 --port 8002
```

### 6. Frontend (puerto 5500) — Nueva terminal
```powershell
cd "C:\PROYECTOS P3\abis-upc\abis-backend\src\main\resources"
python -m http.server 5500
```

## Verificación de servicios

```powershell
# Backend
Invoke-RestMethod -Uri "http://localhost:7000/api/status"
# Resultado: {"service":"javalin","status":"ok"}

# Health
Invoke-RestMethod -Uri "http://localhost:7000/api/health"
# Resultado: {"backend":"ok","biometric":"ok","ocr":"ok",...}

# Biométrico
Invoke-RestMethod -Uri "http://localhost:8001/status"
# Resultado: {"status":"ok","message":"Biometric service ready"}

# OCR
Invoke-RestMethod -Uri "http://localhost:8002/health"
# Resultado: {"status":"ok"}
```

## Flujo de la demo

### Paso 1: Pre-registro de votante
1. Abrir navegador: http://localhost:5500/pages/auth/index.html
2. Llenar formulario con datos del votante:
   - Identificación: 1052041109
   - Primer nombre: Daniel
   - Segundo nombre: David
   - Primer apellido: Turizo
   - Segundo apellido: Chacon
   - Correo: daniel@upc.edu.co
   - Rol: Estudiante (id_rol: 1)
   - Puesto: Puesto 1 (id_puesto: 1)
3. Click en "Registrarse"
4. Esperar respuesta: {"success":true,"identificacion":"1052041109",...}

### Paso 2: Enrolamiento biométrico
1. Abrir navegador: http://localhost:5500/pages/biometric/enroll.html
2. Ingresar identificación: 1052041109
3. Colocar dedo en lector DigitalPersona
4. Esperar confirmación: "Huella enrolada exitosamente"

### Paso 3: Verificación biométrica
1. Abrir navegador: http://localhost:5500/pages/biometric/verify.html
2. Colocar dedo en lector DigitalPersona
3. Esperar identificación: {"matched":true,"identificacion":"1052041109",...}

## Solución de problemas

| Problema | Causa | Solución |
|----------|-------|----------|
| Port 7000 en uso | Proceso anterior | `Stop-Process -Id (Get-NetTCPConnection -LocalPort 7000).OwningProcess` |
| Oracle ORA-01017 | Credenciales incorrectas | Verificar ABIS_DB_USER y ABIS_DB_PASSWORD |
| biometric=offline | FastAPI no iniciada | Verificar que puerto 8001 esté escuchando |
| ocr=offline | FastAPI OCR no iniciada | Verificar que puerto 8002 esté escuchando |
| 500 al registrar | Oracle no disponible | Verificar que Oracle esté en puerto 1521 |

## Credenciales de demo

- **Usuario Oracle**: abisAdmin
- **Contraseña Oracle**: 12345
- **Puerto Oracle**: 1521 (XEPDB1)
- **Puerto Backend**: 7000
- **Puerto Biométrico**: 8001
- **Puerto OCR**: 8002
- **Puerto Frontend**: 5500
