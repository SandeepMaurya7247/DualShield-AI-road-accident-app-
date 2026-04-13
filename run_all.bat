@echo off
echo [1/2] Building and Installing Android App...
call %~dp0gradlew installDebug

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
    npm start
) else (
    echo.
    echo ERROR: Gradle build failed. Please check your phone connection.
    pause
)
