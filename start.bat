@echo off
title Battery Health UI Preview
cd /d "%~dp0preview"
echo.
echo  Battery Health UI Preview
echo  Starting Vite dev server...
echo.

where npm >nul 2>&1
if errorlevel 1 (
    echo  ERROR: Node.js / npm not found.
    echo  Please install Node.js from https://nodejs.org
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo  Installing dependencies...
    npm install
    echo.
)

npm run dev
