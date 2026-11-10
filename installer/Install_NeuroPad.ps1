# ========================================================================
#       NeuroPad - Virtual GamePad Simulation (PowerShell Installer)
# ========================================================================
# Requires: USB Debugging enabled on Android device connected via USB
# ========================================================================

[CmdletBinding()]
param()

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Header {
    Clear-Host
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "      NeuroPad - Virtual GamePad Simulation               " -ForegroundColor Yellow -NoNewline
    Write-Host " [v1.0]" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host ""
}

Write-Header
Write-Host "This script configures your Android phone for standalone"
Write-Host "kernel-level gamepad virtualization without root."
Write-Host ""
Write-Host "Please ensure:" -ForegroundColor Yellow
Write-Host "  1. Your phone is connected to this PC via USB."
Write-Host "  2. USB Debugging is ENABLED in Developer Options."
Write-Host "  3. You tap 'Allow' on your phone when prompted."
Write-Host ""

$confirm = Read-Host "Press ENTER to begin installation (or Ctrl+C to cancel)"
Write-Host ""

# ------------------------------------------------------------------------
# 1. Locate ADB
# ------------------------------------------------------------------------
Write-Host "Searching for adb.exe..." -ForegroundColor Cyan

$AdbCandidatePaths = @(
    Join-Path $ScriptDir "tools\adb.exe",
    Join-Path $ScriptDir "adb.exe",
    Join-Path $ScriptDir "platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$AdbPath = $null
foreach ($path in $AdbCandidatePaths) {
    if (Test-Path $path) {
        $AdbPath = $path
        break
    }
}

if (-not $AdbPath) {
    $whereAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($whereAdb) {
        $AdbPath = $whereAdb.Source
    }
}

if (-not $AdbPath) {
    Write-Host "[ERROR] adb.exe could not be found!" -ForegroundColor Red
    Write-Host "Ensure the 'tools' folder with adb.exe is present in this directory."
    Read-Host "Press ENTER to exit..."
    exit 1
}

Write-Host "[OK] Found ADB: $AdbPath" -ForegroundColor Green
Write-Host ""

# ------------------------------------------------------------------------
# 2. Wait for Device
# ------------------------------------------------------------------------
Write-Host "[1/5] Waiting for connected Android device..." -ForegroundColor Cyan
& $AdbPath wait-for-device
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to communicate with device." -ForegroundColor Red
    exit 1
}
$deviceModel = (& $AdbPath shell getprop ro.product.model).Trim()
Write-Host "[OK] Connected device: $deviceModel" -ForegroundColor Green
Write-Host ""

# ------------------------------------------------------------------------
# 3. Deploy uinput_proto Kernel Daemon
# ------------------------------------------------------------------------
Write-Host "[2/5] Deploying native kernel gamepad daemon (uinput_proto)..." -ForegroundColor Cyan
$uinputBin = Join-Path $ScriptDir "uinput_proto"

if (Test-Path $uinputBin) {
    & $AdbPath push $uinputBin /data/local/tmp/uinput_proto | Out-Null
    & $AdbPath shell chmod 755 /data/local/tmp/uinput_proto | Out-Null
    $isExec = (& $AdbPath shell "[ -x /data/local/tmp/uinput_proto ] && echo YES || echo NO").Trim()
    if ($isExec -eq "YES") {
        Write-Host "[OK] Gamepad daemon deployed to /data/local/tmp/uinput_proto (executable)" -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Deployed but executable check returned: $isExec" -ForegroundColor Yellow
    }
} else {
    Write-Host "[WARNING] uinput_proto binary not found in script folder!" -ForegroundColor Yellow
}
Write-Host ""

# ------------------------------------------------------------------------
# 4. Enable TCP/IP Port 5555 Loopback
# ------------------------------------------------------------------------
Write-Host "[3/5] Enabling ADB TCP loopback on Port 5555..." -ForegroundColor Cyan
& $AdbPath tcpip 5555
Start-Sleep -Seconds 2
& $AdbPath wait-for-device
$tcpPort = (& $AdbPath shell getprop service.adb.tcp.port).Trim()
Write-Host "[OK] ADB TCP Port active: $tcpPort" -ForegroundColor Green
Write-Host ""

# ------------------------------------------------------------------------
# 5. Install APK
# ------------------------------------------------------------------------
Write-Host "[4/5] Installing NeuroPad App (NeuroPad_App.apk)..." -ForegroundColor Cyan
$apkFile = Join-Path $ScriptDir "NeuroPad_App.apk"

if (Test-Path $apkFile) {
    & $AdbPath install -r -d $apkFile
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] NeuroPad App installed successfully!" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] APK installation failed with exit code $LASTEXITCODE." -ForegroundColor Red
    }
} else {
    Write-Host "[ERROR] NeuroPad_App.apk not found in script folder!" -ForegroundColor Red
}
Write-Host ""

# ------------------------------------------------------------------------
# 6. Launch App
# ------------------------------------------------------------------------
Write-Host "[5/5] Launching NeuroPad on phone screen..." -ForegroundColor Cyan
& $AdbPath shell am force-stop com.g8.driver 2>&1 | Out-Null
& $AdbPath shell am start -n com.g8.driver/.MainActivity 2>&1 | Out-Null
Write-Host "[OK] App launched on device!" -ForegroundColor Green
Write-Host ""

# ------------------------------------------------------------------------
# Complete
# ------------------------------------------------------------------------
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "               INSTALLATION COMPLETE!                     " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Cyan
Write-Host " 1. Unplug the USB cable from your PC."
Write-Host " 2. Plug your Gamepad (USB-C or Bluetooth) into your phone."
Write-Host " 3. Toggle 'Gamepad Virtualization' ON inside NeuroPad!"
Write-Host ""
Read-Host "Press ENTER to finish..."
