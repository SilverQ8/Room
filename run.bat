@echo off
setlocal
cd /d "%~dp0"
echo [3D Room Planner] Launching 3D Furniture Planner Application...

java --module-path "javafx-sdk-21.0.3/lib" --add-modules javafx.controls,javafx.graphics,javafx.fxml -cp bin com.roomplanner.Main

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Application failed to start.
    pause
)
