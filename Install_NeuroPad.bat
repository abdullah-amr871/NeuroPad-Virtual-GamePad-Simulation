@echo off
setlocal
title NeuroPad - Virtual GamePad Simulation
color 0A

echo ==========================================================
echo        NeuroPad - Virtual GamePad Simulation
echo               (1-Click Installer)
echo ==========================================================
echo.
echo This script installs the NeuroPad app and activates offline
echo Port 5555 gamepad virtualization on your Android device.
echo.
echo Please ensure:
echo 1. Your phone is connected to this PC via USB cable.
echo 2. USB Debugging is ENABLED in Developer Options.
echo.
echo Press any key to start...
pause >nul

echo.
echo Searching for adb.exe...

:: Resolve ADB path using goto to avoid nested-if quoting bugs
set "ADB="
set "MYDIR=%~dp0"

if exist "%MYDIR%tools\adb.exe" (
    set "ADB=%MYDIR%tools\adb.exe"
    goto :found_adb
)
if exist "%MYDIR%adb.exe" (
    set "ADB=%MYDIR%adb.exe"
    goto :found_adb
)
if exist "%MYDIR%platform-tools\adb.exe" (
    set "ADB=%MYDIR%platform-tools\adb.exe"
    goto :found_adb
)
if exist "%LocalAppData%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LocalAppData%\Android\Sdk\platform-tools\adb.exe"
    goto :found_adb
)
where adb.exe >nul 2>nul
if %errorlevel% equ 0 (
    for /f "delims=" %%A in ('where adb.exe') do (
        set "ADB=%%A"
        goto :found_adb
    )
)

echo.
echo [ERROR] adb.exe could not be found!
echo Please make sure the 'tools' folder with adb.exe is in the same directory.
echo.
pause
exit /b 1

:found_adb
echo Found ADB at: %ADB%
echo.

echo [1/5] Waiting for connected Android device...
"%ADB%" wait-for-device
echo [OK] Phone detected!

echo.
echo [2/5] Deploying native kernel gamepad daemon (uinput_proto)...
if exist "%MYDIR%uinput_proto" (
    "%ADB%" push "%MYDIR%uinput_proto" /data/local/tmp/uinput_proto >nul 2>&1
    "%ADB%" shell chmod 755 /data/local/tmp/uinput_proto >nul 2>&1
    echo [OK] Gamepad daemon deployed to /data/local/tmp/uinput_proto
) else (
    echo [WARNING] uinput_proto binary not found in script folder!
)

echo.
echo [3/5] Enabling ADB TCP loopback on Port 5555...
"%ADB%" tcpip 5555

echo Waiting for device to reconnect...
timeout /t 3 /nobreak >nul
"%ADB%" wait-for-device

echo.
echo [4/5] Installing NeuroPad App (NeuroPad_App.apk)...
if exist "%MYDIR%NeuroPad_App.apk" (
    "%ADB%" install -r -d "%MYDIR%NeuroPad_App.apk"
    echo [OK] App installation complete!
) else (
    echo [ERROR] NeuroPad_App.apk not found in installer directory!
)

echo.
echo [5/5] Launching NeuroPad on phone screen...
"%ADB%" shell am force-stop com.g8.driver >nul 2>&1
"%ADB%" shell am start -n com.g8.driver/.MainActivity >nul 2>&1
echo [OK] App launched on device!

echo.
echo ==========================================================
echo                INSTALLATION COMPLETE!
echo ==========================================================
echo.
echo NEXT STEPS:
echo  1. Unplug the USB cable from your PC.
echo  2. Plug your Gamepad (USB-C or Bluetooth) into your phone.
echo  3. Toggle 'Gamepad Virtualization' ON inside NeuroPad!
echo.
pause
