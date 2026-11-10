@echo off
rem NeuroPad 2 - one-click installer for Windows.
rem The work is done by Install_NeuroPad.ps1; this file only makes it double-clickable.
title NeuroPad 2 - Installer
if not exist "%~dp0Install_NeuroPad.ps1" (
    echo [ERROR] Install_NeuroPad.ps1 is missing. Extract the whole ZIP first.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install_NeuroPad.ps1"
