@echo off
adb devices | findstr /R /C:"[0-9a-zA-Z].*device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No Android device found. Please connect your phone via USB and enable USB Debugging.
    adb devices
    pause
    exit /b 1
)
echo [1/2] Building and Installing Android App...
call %~dp0gradlew installDebug 2>nul

if %ERRORLEVEL% EQU 0 (
    echo [2/2] Launching App on Phone...
    adb shell am start -n com.team404.dualshield/com.team404.dualshield.MainActivity
    echo.
    echo SUCCESS: App is running! 
    echo --------------------------------------------------
    echo [STARTING BACKEND SERVER]
    echo Press Ctrl+C in this window to stop the server.
    echo --------------------------------------------------
    echo.
    
    cd /d %~dp0backend
    call npm install
    :: Ensure port 5000 is free before starting (Native Windows command)
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5000') do taskkill /f /pid %%a 2>nul
    npm start
) else (
    echo.
    echo ERROR: Gradle build failed. Run with --stacktrace for details.
    pause
)
