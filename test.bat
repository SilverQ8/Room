@echo off
setlocal
cd /d "%~dp0"
if "%JAVAFX_LIB%"=="" set "JAVAFX_LIB=%~dp0..\Room-main\javafx-sdk-21.0.3\lib"

call "%~dp0compile.bat"
if %ERRORLEVEL% neq 0 exit /b 1

if not exist bin-test mkdir bin-test
javac -encoding UTF-8 --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.swing -cp bin -d bin-test src\test\java\com\roomplanner\*.java
if %ERRORLEVEL% neq 0 exit /b 1

echo.
echo === Model check (no UI) ===
java -Dfile.encoding=UTF-8 -cp bin;bin-test com.roomplanner.ModelCheck
if %ERRORLEVEL% neq 0 exit /b 1

echo.
echo === UI smoke test (a window appears briefly, screenshots go to smoke-out) ===
java -Dfile.encoding=UTF-8 --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.swing -cp bin;bin-test com.roomplanner.SmokeTest smoke-out
