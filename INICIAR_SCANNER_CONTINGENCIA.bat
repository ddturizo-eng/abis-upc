@echo off
chcp 65001 >nul
title ABIS-UPC - Scanner Contingencia

set "ABIS_ROOT=C:\PROYECTOS P3\abis-upc"
set "ABIS_ENV_FILE=%ABIS_ROOT%\.env"

if exist "%ABIS_ENV_FILE%" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ABIS_ENV_FILE%") do (
        if not "%%A"=="" set "%%A=%%B"
    )
)

if not defined SCANNER_PORT set "SCANNER_PORT=COM5"
if not defined SCANNER_BAUD set "SCANNER_BAUD=9600"
if not defined ABIS_JAVA_URL set "ABIS_JAVA_URL=http://localhost:7000"
if not defined SCANNER_ID set "SCANNER_ID=YHD9601D-01"

echo ==========================================
echo   ABIS-UPC - Scanner de Contingencia
echo ==========================================
echo Puerto:  %SCANNER_PORT%
echo Baud:    %SCANNER_BAUD%
echo Backend: %ABIS_JAVA_URL%
echo.

python -m pip install pyserial requests python-dotenv --quiet

cd /d "%ABIS_ROOT%"
python scripts\contingencia_scanner.py --port "%SCANNER_PORT%" --baud %SCANNER_BAUD% --java-url "%ABIS_JAVA_URL%" --scanner-id "%SCANNER_ID%"

echo.
echo Scanner detenido.
pause
