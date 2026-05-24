@echo off
chcp 65001 >nul
title ABIS-UPC - Detener Servicios

echo ==========================================
echo   ABIS-UPC - Detener Servicios
echo ==========================================
echo.
echo NOTA: Oracle XE NO se detiene (dejarlo corriendo).
echo.

echo Deteniendo servicios ABIS-UPC...
echo.

echo [1] Frontend (puerto 5500)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5500 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [2] Email Service (puerto 8010)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8010 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [3] OCR Service (puerto 8002)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8002 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [4] Biometrico Service (puerto 8001)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8001 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [5] Backend Java (puerto 7000)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :7000 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [6] NativeService (puerto 8765)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8765 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [7] Scanner de contingencia...
wmic process where "CommandLine like '%%contingencia_scanner.py%%'" call terminate >nul 2>&1
echo     Procesos de scanner cerrados si estaban activos

echo.
echo ==========================================
echo   Servicios ABIS-UPC detenidos
echo ==========================================
echo.
pause
