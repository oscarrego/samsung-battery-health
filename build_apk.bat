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
    
    for %%F in ("MyBattery-debug.apk") do set APK_SIZE=%%~zF
    
    color 0A
    echo.
    echo ===================================================
    echo  SUCCESS: APK built and updated successfully!
    echo ===================================================
    echo.
    echo  File:     %~dp0MyBattery-debug.apk
    echo  Size:     !APK_SIZE! bytes
    echo  Updated:  %date% %time%
    echo.
    echo ===================================================
    echo.
    echo  [O] Open APK in File Explorer
    echo  [X] Exit
    echo.
    set /p USER_CHOICE="Select an option (O/X) or press Enter to finish: "
    if /i "!USER_CHOICE!"=="O" (
        explorer.exe /select,"MyBattery-debug.apk"
    )
    goto end
) else (
    echo [WARNING] app-debug.apk was not found in app\build\outputs\apk\debug\
    goto fail
)

:fail
color 0C
echo.
echo ===================================================
echo  BUILD FAILED! Check the error details above.
echo ===================================================
echo.
pause
exit /b 1

:end
exit /b 0
