@echo off
setlocal
cd /d "%~dp0"
echo [3D Room Planner] Compiling Java source files...

if not exist bin mkdir bin

javac -encoding UTF-8 --module-path "javafx-sdk-21.0.3/lib" --add-modules javafx.controls,javafx.graphics,javafx.fxml -d bin src/main/java/com/roomplanner/model/*.java src/main/java/com/roomplanner/view/*.java src/main/java/com/roomplanner/*.java

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Compilation completed successfully!
) else (
    echo [ERROR] Compilation failed.
)
pause
