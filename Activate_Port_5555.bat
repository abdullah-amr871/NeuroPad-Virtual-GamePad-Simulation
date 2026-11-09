@echo off
setlocal
title NEUROPAD - Port 5555 Activator
color 0A

echo =======================================================
echo NEUROPAD - Port 5555 Activator
echo =======================================================
echo.
echo This script will configure your phone to accept ADB connections
echo on Port 5555 so that you can use the driver offline.
echo.
echo Please ensure:
echo 1. Your phone is connected to this PC via a USB cable.
echo 2. USB Debugging is ENABLED in Developer Options on your phone.
echo 3. You have allowed USB Debugging authorization on your phone screen.
echo.
pause
echo.
echo Searching for adb.exe...

:: Step 1: Look for bundled adb.exe in tools\ subfolder next to this script
set "ADB="
set "MYDIR=%~dp0"
if exist "%MYDIR%tools\adb.exe" (
    set "ADB=%MYDIR%tools\adb.exe"
    goto :found_adb
)

:: Step 2: Look for adb.exe right next to this script
if exist "%MYDIR%adb.exe" (
    set "ADB=%MYDIR%adb.exe"
    goto :found_adb
)

:: Step 3: Look for adb.exe in platform-tools next to this script
if exist "%MYDIR%platform-tools\adb.exe" (
    set "ADB=%MYDIR%platform-tools\adb.exe"
    goto :found_adb
)

:: Step 4: Check common Android SDK install locations
if exist "%LocalAppData%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LocalAppData%\Android\Sdk\platform-tools\adb.exe"
    goto :found_adb
)

:: Step 5: Check system PATH
where adb.exe >nul 2>nul
if %errorlevel% equ 0 (
    for /f "delims=" %%A in ('where adb.exe') do (
        set "ADB=%%A"
        goto :found_adb
    )
)

:: Nothing found
echo.
echo [ERROR] adb.exe could not be found!
echo.
echo To fix this, place a 'tools' folder containing adb.exe
echo in the same directory as this script, or install Android
echo Platform Tools and add it to your system PATH.
echo.
pause
exit /b 1

:found_adb
echo Found adb at: %ADB%
echo.

echo Checking connected devices...
"%ADB%" devices
echo.

echo Deploying gamepad daemon (uinput_proto)...
if exist "%MYDIR%uinput_proto" (
    "%ADB%" push "%MYDIR%uinput_proto" /data/local/tmp/uinput_proto >nul 2>&1
    "%ADB%" shell chmod 755 /data/local/tmp/uinput_proto >nul 2>&1
    echo [OK] Gamepad daemon deployed.
) else (
    echo [SKIP] uinput_proto not found in script folder.
)
echo.

echo Setting TCP/IP port to 5555...
"%ADB%" tcpip 5555
if %errorlevel% equ 0 (
    echo.
    echo =======================================================
    echo SUCCESS! Port 5555 has been activated on your phone.
    echo =======================================================
    echo.
    echo You can now:
    echo  1. Unplug the USB cable.
    echo  2. Plug your Gamepad into your phone.
    echo  3. Open NeuroPad and toggle Virtualization ON!
    echo =======================================================
) else (
    echo.
    echo [ERROR] Failed to set port 5555.
    echo Please check if your phone is properly connected
    echo and USB Debugging is authorized.
)
echo.
pause
