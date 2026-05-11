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

echo [2] OCR Service (puerto 8002)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8002 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [3] Biometrico Service (puerto 8001)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8001 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo [4] Backend Java (puerto 7000)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :7000 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo     Detenido
)

echo.
echo ==========================================
echo   Servicios ABIS-UPC detenidos
echo ==========================================
echo.
pause