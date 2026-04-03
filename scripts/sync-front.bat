@echo off
title ABIS-UPC :: Sync Frontend + Kiosk Launcher
color 0A
chcp 65001 >nul

echo ============================================
echo   ABIS-UPC  ::  Sync + Kiosk Launcher
echo ============================================

set PROJECT_ROOT=C:\PROYECTOS P3\abis-upc
set FRONTEND_SRC=%PROJECT_ROOT%\abis-frontend
set BACKEND_RES=%PROJECT_ROOT%\abis-backend\src\main\resources

echo [1/3] Sincronizando frontend...

xcopy "%FRONTEND_SRC%\pages" "%BACKEND_RES%\pages\" /S /E /Y /I >nul
xcopy "%FRONTEND_SRC%\assets" "%BACKEND_RES%\assets\" /S /E /Y /I >nul 2>nul
xcopy "%FRONTEND_SRC%\styles" "%BACKEND_RES%\styles\" /S /E /Y /I >nul 2>nul
xcopy "%FRONTEND_SRC%\components" "%BACKEND_RES%\components\" /S /E /Y /I >nul 2>nul

echo     Frontend sincronizado.

echo [2/3] Buscando Chrome...
set CHROME="C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% set CHROME="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"

echo [3/3] Lanzando Chrome en modo Kiosk...
start "" %CHROME% --kiosk --disable-infobars --no-first-run http://localhost:7000/pages/auth/index.html

echo ============================================
echo   Sistema ABIS-UPC en ejecucion.
echo ============================================
pause
