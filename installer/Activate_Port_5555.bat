@echo off
rem NeuroPad 2 - re-open ADB port 5555 after the phone was rebooted.
title NeuroPad 2 - Port 5555 Activator
if not exist "%~dp0Activate_Port_5555.ps1" (
    echo [ERROR] Activate_Port_5555.ps1 is missing. Extract the whole ZIP first.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Activate_Port_5555.ps1"
