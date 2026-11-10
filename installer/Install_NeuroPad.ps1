# ========================================================================
#  NeuroPad 2 - one-click installer (Windows PowerShell)
#  Needs: USB debugging enabled, phone connected with a data cable.
# ========================================================================
$ErrorActionPreference = 'Continue'
$Host.UI.RawUI.WindowTitle = 'NeuroPad 2 - Installer'

$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$Pkg          = 'io.github.abdullahamr871.neuropad'
$OldPkg       = 'com.g8.driver'
$Apk          = Join-Path $ScriptDir 'NeuroPad.apk'
$Daemon       = Join-Path $ScriptDir 'uinput_proto'
$RemoteDaemon = '/data/local/tmp/uinput_proto'

function Info($m) { Write-Host $m -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[OK] $m" -ForegroundColor Green }
function Warn($m) { Write-Host "[!] $m" -ForegroundColor Yellow }
function Die($m)  {
    Write-Host "[ERROR] $m" -ForegroundColor Red
    Write-Host ''
    Read-Host 'Press ENTER to close'
    exit 1
}

Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host '  NeuroPad 2 - rumble for USB gamepads on Android' -ForegroundColor Yellow
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ''
Write-Host 'Before you continue:'
Write-Host '  1. Connect the phone to this PC with a USB data cable.'
Write-Host '  2. Enable USB debugging in Developer options.'
Write-Host '  3. Tap "Allow" on the phone when it asks.'
Write-Host ''
Read-Host 'Press ENTER to start (Ctrl+C to cancel)' | Out-Null
Write-Host ''

# ---- 1. adb ---------------------------------------------------------------
$Adb = $null
$candidates = @(
    (Join-Path $ScriptDir 'tools\adb.exe'),
    (Join-Path $ScriptDir 'adb.exe'),
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
foreach ($c in $candidates) { if (-not $Adb -and (Test-Path $c)) { $Adb = $c } }
if (-not $Adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $Adb = $cmd.Source }
}
if (-not $Adb) { Die 'adb.exe was not found. Extract the whole ZIP and keep the "tools" folder next to this script.' }
Ok "Using adb: $Adb"
if (-not (Test-Path $Apk)) { Die 'NeuroPad.apk is missing. Extract the whole ZIP and run the installer from that folder.' }

# ---- 2. device ------------------------------------------------------------
# With several devices attached (for example the same phone over USB and over Wi-Fi, or an
# emulator) adb refuses to guess. Pick the USB one and use it for every command.
function Select-Device {
    for ($i = 0; $i -lt 60; $i++) {
        $lines = (& $Adb devices | Out-String) -split "`r?`n" | Where-Object { $_ -match "`t" }
        $ready = @($lines | Where-Object { $_ -match "`tdevice$" } | ForEach-Object { ($_ -split "`t")[0] })
        $usb = @($ready | Where-Object { $_ -notmatch ':' -and $_ -notmatch '^emulator-' })
        if ($usb.Count -eq 1) { return $usb[0] }
        if ($usb.Count -gt 1) { Die "More than one phone is connected by USB. Unplug the others and run this again." }
        if ($ready.Count -eq 1) { return $ready[0] }
        if ($lines | Where-Object { $_ -match "`tunauthorized$" }) {
            if ($i -eq 0) { Warn 'Tap "Allow" on the phone to authorise this computer...' }
        }
        Start-Sleep -Seconds 1
    }
    Die 'No phone was found. Check the cable (it must be a data cable) and that USB debugging is enabled.'
}

Info '[1/5] Waiting for the phone...'
& $Adb start-server 2>$null | Out-Null
$env:ANDROID_SERIAL = Select-Device
$model = (& $Adb shell getprop ro.product.model | Out-String).Trim()
$abi   = (& $Adb shell getprop ro.product.cpu.abi | Out-String).Trim()
Ok "Connected: $model ($abi)"
if ($abi -ne 'arm64-v8a') { Warn "NeuroPad needs a 64-bit ARM phone; '$abi' will most likely not work." }

# ---- 3. driver ------------------------------------------------------------
Info '[2/5] Installing the driver...'
if (Test-Path $Daemon) {
    & $Adb push $Daemon $RemoteDaemon | Out-Null
    if ($LASTEXITCODE -ne 0) { Die 'Could not copy the driver to the phone.' }
    & $Adb shell chmod 755 $RemoteDaemon
    $version = (& $Adb shell "$RemoteDaemon --version" 2>$null | Out-String).Trim()
    if ($version) { Ok "Driver $version installed" } else { Warn 'Driver copied, but it did not report a version.' }
} else {
    Warn 'uinput_proto not found next to the script; the app will install its own copy.'
}

# ---- 4. app ---------------------------------------------------------------
Info '[3/5] Installing the NeuroPad app...'
$packages = (& $Adb shell pm list packages | Out-String) -split "`r?`n"
if ($packages -contains "package:$OldPkg") {
    Warn "Removing NeuroPad 1.0 ($OldPkg); version 2 is a separate app with its own settings."
    & $Adb uninstall $OldPkg 2>$null | Out-Null
}
& $Adb install -r $Apk
if ($LASTEXITCODE -ne 0) { Die 'The app could not be installed. On Xiaomi phones enable "Install via USB" in Developer options.' }
Ok 'App installed'

# ---- 5. port 5555 ---------------------------------------------------------
Info '[4/5] Opening ADB port 5555 on the phone...'
& $Adb tcpip 5555 2>$null | Out-Null
Start-Sleep -Seconds 3
& $Adb wait-for-device
$port = (& $Adb shell getprop service.adb.tcp.port | Out-String).Trim()
if ($port -eq '5555') { Ok 'Port 5555 is open' } else { Warn "The phone reports port '$port'. Run Activate_Port_5555.bat if the app cannot connect." }

# ---- 6. launch ------------------------------------------------------------
Info '[5/5] Starting NeuroPad...'
& $Adb shell am start -n "$Pkg/.ui.MainActivity" --es neuropad_mode port5555 2>$null | Out-Null
Ok 'NeuroPad is open on the phone'

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Green
Write-Host '  Done!' -ForegroundColor Yellow
Write-Host '==========================================================' -ForegroundColor Green
Write-Host ' 1. Unplug the phone from the PC.'
Write-Host ' 2. Plug your USB gamepad into the phone.'
Write-Host ' 3. In NeuroPad, tap the big power button.'
Write-Host ''
Write-Host ' After a reboot of the phone, run Activate_Port_5555.bat again'
Write-Host ' (or pair NeuroPad with Wireless debugging once and forget the PC).'
Write-Host ''
Read-Host 'Press ENTER to close' | Out-Null
