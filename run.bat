@echo off
setlocal
cd /d "%~dp0"

if "%JAVAFX_LIB%"=="" if exist "%~dp0javafx-sdk-21.0.3\lib" set "JAVAFX_LIB=%~dp0javafx-sdk-21.0.3\lib"
if "%JAVAFX_LIB%"=="" set "JAVAFX_LIB=%~dp0..\Room-main\javafx-sdk-21.0.3\lib"

call "%~dp0compile.bat"
if %ERRORLEVEL% neq 0 exit /b 1

echo [Room Planner] Starting application...
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.swing,javafx.graphics -cp bin com.roomplanner.Main
