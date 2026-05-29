@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JDK_DIR=%SCRIPT_DIR%jdk"
set "JAR=%SCRIPT_DIR%KMBA.jar"

if not exist "%JAR%" (
    echo [ERROR] KMBA.jar not found in %SCRIPT_DIR%
    pause
    exit /b 1
)

if exist "%JDK_DIR%\bin\java.exe" (
    set "JAVA=%JDK_DIR%\bin\java.exe"
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Java not found. Please install JDK 8+ or use the with-jdk package.
        pause
        exit /b 1
    )
    set "JAVA=java"
)

echo Starting KMBA...
echo Access: http://localhost:9099
echo Press Ctrl+C to stop.
echo.

"%JAVA%" -jar "%JAR%"

pause
