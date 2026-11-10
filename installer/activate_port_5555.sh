#!/usr/bin/env bash
# NeuroPad 2 - re-open ADB port 5555 after the phone was rebooted (Linux & macOS).
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB=""
for candidate in "$(command -v adb 2>/dev/null)" "$SCRIPT_DIR/tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then ADB="$candidate"; break; fi
done
if [ -z "$ADB" ]; then
    echo "[ERROR] adb was not found. Install Android platform-tools first."
    exit 1
fi

echo "Connect the phone with USB debugging enabled..."
"$ADB" wait-for-device
"$ADB" tcpip 5555 >/dev/null 2>&1
sleep 3
"$ADB" wait-for-device
PORT="$("$ADB" shell getprop service.adb.tcp.port | tr -d '\r')"
if [ "$PORT" = "5555" ]; then
    echo "[OK] Port 5555 is open. Unplug the cable, open NeuroPad and tap the power button."
else
    echo "[!] The phone reports port '$PORT'. Unplug, reconnect and try again."
    exit 1
fi
