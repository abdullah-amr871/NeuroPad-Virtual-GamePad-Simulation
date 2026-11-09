# ========================================================================
#            NeuroPad - Port 5555 Activator (PowerShell)
# ========================================================================

[CmdletBinding()]
param()

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Clear-Host
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "           NEUROPAD - Port 5555 Activator             " -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script configures your phone to accept local ADB"
Write-Host "connections on Port 5555 so virtualization works offline."
Write-Host ""
Write-Host "Please ensure:" -ForegroundColor Yellow
Write-Host " 1. Your phone is connected via USB."
Write-Host " 2. USB Debugging is ENABLED in Developer Options."
Write-Host " 3. USB Debugging authorization is allowed on your screen."
Write-Host ""

Read-Host "Press ENTER to continue..."
Write-Host ""

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
    Read-Host "Press ENTER to exit..."
    exit 1
}

Write-Host "Checking connected devices..." -ForegroundColor Cyan
& $AdbPath devices
Write-Host ""

$uinputBin = Join-Path $ScriptDir "uinput_proto"
if (Test-Path $uinputBin) {
    Write-Host "Deploying gamepad daemon (uinput_proto)..." -ForegroundColor Cyan
    & $AdbPath push $uinputBin /data/local/tmp/uinput_proto | Out-Null
    & $AdbPath shell chmod 755 /data/local/tmp/uinput_proto | Out-Null
    Write-Host "[OK] Gamepad daemon deployed." -ForegroundColor Green
}

Write-Host "Activating TCP/IP Port 5555..." -ForegroundColor Cyan
& $AdbPath tcpip 5555
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=======================================================" -ForegroundColor Green
    Write-Host "SUCCESS! Port 5555 has been activated on your phone." -ForegroundColor Yellow
    Write-Host "=======================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Cyan
    Write-Host " 1. Unplug the USB cable."
    Write-Host " 2. Plug your Gamepad into your phone."
    Write-Host " 3. Open NeuroPad and toggle Virtualization ON!"
} else {
    Write-Host "[ERROR] Failed to set port 5555. Check USB connection and debugging authorization." -ForegroundColor Red
}

Write-Host ""
Read-Host "Press ENTER to exit..."
