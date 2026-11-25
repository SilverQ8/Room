@echo off
setlocal
cd /d "%~dp0"

if "%JAVAFX_LIB%"=="" if exist "%~dp0javafx-sdk-21.0.3\lib" set "JAVAFX_LIB=%~dp0javafx-sdk-21.0.3\lib"
if "%JAVAFX_LIB%"=="" set "JAVAFX_LIB=%~dp0..\Room-main\javafx-sdk-21.0.3\lib"

if not exist "%JAVAFX_LIB%" (
    echo [ERROR] JavaFX lib folder not found: %JAVAFX_LIB%
    exit /b 1
)
if not exist bin mkdir bin

echo [Room Planner] Compiling...
javac -encoding UTF-8 --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.swing,javafx.graphics -d bin src\main\java\com\roomplanner\model\*.java src\main\java\com\roomplanner\ui\*.java src\main\java\com\roomplanner\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed.
    exit /b 1
)
echo [SUCCESS] Compiled to bin\
