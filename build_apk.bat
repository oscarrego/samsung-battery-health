@echo off
setlocal enabledelayedexpansion
title MyBattery - 1-Tap APK Builder
cd /d "%~dp0"

echo ===================================================
echo           MyBattery APK One-Tap Builder
echo ===================================================
echo.
echo [1/3] Checking environment...

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found in "%~dp0"
    goto fail
)

echo [2/3] Building debug APK with Gradle...
echo Please wait, compiling Kotlin sources and packaging APK...
echo.

call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo [ERROR] Gradle build failed. Check the error messages above.
    goto fail
)

echo.
echo [3/3] Updating root APK file...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "MyBattery-debug.apk" >nul
    echo.
    echo ===================================================
    echo  SUCCESS: APK built and updated successfully!
    echo ===================================================
    echo.
    echo  APK Location: %~dp0MyBattery-debug.apk
    echo.
) else (
    echo [WARNING] app-debug.apk was not found in app\build\outputs\apk\debug\
    goto fail
)

goto end

:fail
echo.
echo ===================================================
echo  BUILD FAILED!
echo ===================================================
echo.

:end
echo Press any key to exit...
pause >nul
