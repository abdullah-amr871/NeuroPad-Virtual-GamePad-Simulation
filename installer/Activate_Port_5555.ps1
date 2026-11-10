# NeuroPad 2 - re-open ADB port 5555 after the phone was rebooted (Windows PowerShell).
$ErrorActionPreference = 'Continue'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$Adb = $null
foreach ($c in @((Join-Path $ScriptDir 'tools\adb.exe'), (Join-Path $ScriptDir 'adb.exe'))) {
    if (-not $Adb -and (Test-Path $c)) { $Adb = $c }
}
if (-not $Adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $Adb = $cmd.Source }
}
if (-not $Adb) {
    Write-Host '[ERROR] adb.exe was not found. Keep the "tools" folder next to this script.' -ForegroundColor Red
    Read-Host 'Press ENTER to close' | Out-Null
    exit 1
}

Write-Host 'Connect the phone with USB debugging enabled...' -ForegroundColor Cyan
& $Adb wait-for-device
& $Adb tcpip 5555 2>$null | Out-Null
Start-Sleep -Seconds 3
& $Adb wait-for-device
$port = (& $Adb shell getprop service.adb.tcp.port | Out-String).Trim()
if ($port -eq '5555') {
    Write-Host '[OK] Port 5555 is open. Unplug the cable, open NeuroPad and tap the power button.' -ForegroundColor Green
} else {
    Write-Host "[!] The phone reports port '$port'. Unplug, reconnect and try again." -ForegroundColor Yellow
}
Read-Host 'Press ENTER to close' | Out-Null
