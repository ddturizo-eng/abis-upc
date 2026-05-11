@echo off
chcp 65001 >nul
title ABIS-UPC - Iniciar Servicios

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Ejecuta este script como ADMINISTRADOR
    echo Clic derecho sobre el archivo ^> Ejecutar como administrador
    pause
    exit /b
)

echo ==========================================
echo   ABIS-UPC - Inicio de Servicios
echo ==========================================
echo.

echo [1/7] Verificando Oracle XE...
echo     Oracle se mantiene corriendo (no se toca)
echo.

echo [2/7] Iniciando NativeService (huella digital, puerto 8765)...
net start DpHost >nul 2>&1
if %errorLevel% == 0 (
    echo     OK - DpHost iniciado
) else (
    echo     OK - DpHost ya estaba corriendo o no existe
)
if exist "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48\NativeService.exe" (
    start "ABIS-NativeService" /min "C:\PROYECTOS P3\abis-upc\abis-native\NativeService\bin\Debug\net48\NativeService.exe"
    timeout /t 3 /nobreak >nul
    echo     NativeService iniciado en puerto 8765
) else (
    echo     ADVERTENCIA: NativeService.exe no encontrado en la ruta esperada
)
echo.

echo [3/7] Verificando/instalando dependencias biom%'etricas...
pip install python-dotenv httpx pymupdf --quiet 2>nul
echo     Dependencias biometricas OK
echo.

echo [4/7] Iniciando Servicio Biom%'etrico (puerto 8001)...
cd /d "C:\PROYECTOS P3\abis-upc\abis-biometric"
start "ABIS-Biometric" cmd /k "cd /d "C:\PROYECTOS P3\abis-upc\abis-biometric" && venv\Scripts\activate && uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload"
echo     Biom%'etrico iniciado en puerto 8001
echo.

echo [5/7] Verificando/instalando dependencias OCR...
cd /d "C:\PROYECTOS P3\abis-upc\abis-ocr"
pip install -r requirements.txt --quiet 2>nul || echo     Dependencias OCR con advertencias, continuando...
echo.

echo [6/7] Iniciando Servicio OCR (puerto 8002)...
if exist "api\main.py" (
    start "ABIS-OCR" cmd /k "cd /d "C:\PROYECTOS P3\abis-upc\abis-ocr" && venv\Scripts\activate && uvicorn api.main:app --host 0.0.0.0 --port 8002"
    echo     OCR iniciado en puerto 8002
) else (
    echo     ADVERTENCIA: No se encontro api/main.py en abis-ocr
)
echo.

echo [7/7] Iniciando Backend Java (puerto 7000)...
cd /d "C:\PROYECTOS P3\abis-upc\abis-backend\target"
if not exist "abis-backend-1.0-SNAPSHOT.jar" (
    echo     ERROR: No se encontro abis-backend-1.0-SNAPSHOT.jar
    echo     Compila el proyecto primero: cd C:\PROYECTOS P3\abis-upc\abis-backend ^&^& mvn package
    pause
    exit /b 1
)
start "ABIS-Backend" cmd /k "set ABIS_DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1&&set ABIS_DB_USER=abisAdmin&&set ABIS_DB_PASSWORD=12345&&set BIOMETRIC_SERVICE_URL=http://localhost:8001&&set OCR_SERVICE_URL=http://localhost:8002&&java -jar abis-backend-1.0-SNAPSHOT.jar"
echo     Backend Java iniciado en puerto 7000
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
echo.
echo Abriendo navegador con el frontend...
timeout /t 5 /nobreak >nul
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
echo.
echo Listo. Usa DETENER_SERVICIOS.bat para detener.
echo.
pause