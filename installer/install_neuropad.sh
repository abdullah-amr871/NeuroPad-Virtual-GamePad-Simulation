#!/usr/bin/env bash
# ========================================================================
#  NeuroPad 2 - one-click installer (Linux & macOS)
#  Needs: USB debugging enabled, phone connected with a data cable.
# ========================================================================
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG="io.github.abdullahamr871.neuropad"
OLD_PKG="com.g8.driver"
APK="$SCRIPT_DIR/NeuroPad.apk"
DAEMON="$SCRIPT_DIR/uinput_proto"
REMOTE_DAEMON="/data/local/tmp/uinput_proto"

C='\033[0;36m'; G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; N='\033[0m'
info() { echo -e "${C}$*${N}"; }
ok()   { echo -e "${G}[OK]${N} $*"; }
warn() { echo -e "${Y}[!]${N} $*"; }
die()  { echo -e "${R}[ERROR]${N} $*"; exit 1; }

echo -e "${C}==========================================================${N}"
echo -e "${Y}  NeuroPad 2 - rumble for USB gamepads on Android${N}"
echo -e "${C}==========================================================${N}"
echo
echo "Before you continue:"
echo "  1. Connect the phone to this computer with a USB data cable."
echo "  2. Enable USB debugging in Developer options."
echo "  3. Tap 'Allow' on the phone when it asks."
echo
read -rp "Press ENTER to start (Ctrl+C to cancel)... " _
echo

# ---- 1. adb ---------------------------------------------------------------
ADB=""
for candidate in "$(command -v adb 2>/dev/null)" "$SCRIPT_DIR/tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then ADB="$candidate"; break; fi
done
[ -n "$ADB" ] || die "adb was not found. Install it first:
    macOS:  brew install android-platform-tools
    Debian/Ubuntu:  sudo apt install adb"
ok "Using adb: $ADB"

[ -f "$APK" ] || die "NeuroPad.apk is missing. Extract the whole ZIP and run the script from that folder."

# ---- 2. device ------------------------------------------------------------
# With several devices attached (the same phone over USB and Wi-Fi, an emulator, ...) adb
# refuses to guess. Pick the USB one and use it for every command.
select_device() {
    local i ready usb
    for i in $(seq 1 60); do
        ready="$("$ADB" devices | tr -d '\r' | awk -F'\t' '$2=="device"{print $1}')"
        usb="$(printf '%s\n' "$ready" | grep -v ':' | grep -v '^emulator-' | grep -v '^$')"
        if [ "$(printf '%s\n' "$usb" | grep -c .)" -eq 1 ]; then printf '%s' "$usb"; return 0; fi
        if [ "$(printf '%s\n' "$usb" | grep -c .)" -gt 1 ]; then return 2; fi
        if [ "$(printf '%s\n' "$ready" | grep -c .)" -eq 1 ]; then printf '%s' "$ready"; return 0; fi
        sleep 1
    done
    return 1
}

info "[1/5] Waiting for the phone..."
"$ADB" start-server >/dev/null 2>&1
ANDROID_SERIAL="$(select_device)"
case $? in
    0) export ANDROID_SERIAL ;;
    2) die "More than one phone is connected by USB. Unplug the others and run this again." ;;
    *) die "No phone was found. Check the cable, unlock the phone and allow USB debugging." ;;
esac
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
ok "Connected: $MODEL ($ABI)"
[ "$ABI" = "arm64-v8a" ] || warn "NeuroPad needs a 64-bit ARM phone; '$ABI' will most likely not work."

# ---- 3. driver ------------------------------------------------------------
info "[2/5] Installing the driver..."
if [ -f "$DAEMON" ]; then
    "$ADB" push "$DAEMON" "$REMOTE_DAEMON" >/dev/null || die "Could not copy the driver to the phone."
    "$ADB" shell chmod 755 "$REMOTE_DAEMON"
    VERSION="$("$ADB" shell "$REMOTE_DAEMON --version" 2>/dev/null | tr -d '\r')"
    if [ -n "$VERSION" ]; then ok "Driver $VERSION installed"; else warn "Driver copied, but it did not report a version."; fi
else
    warn "uinput_proto not found next to the script; the app will install its own copy."
fi

# ---- 4. app ---------------------------------------------------------------
info "[3/5] Installing the NeuroPad app..."
if "$ADB" shell pm list packages | tr -d '\r' | grep -qx "package:$OLD_PKG"; then
    warn "Removing NeuroPad 1.0 ($OLD_PKG); version 2 is a separate app with its own settings."
    "$ADB" uninstall "$OLD_PKG" >/dev/null 2>&1 || true
fi
if ! "$ADB" install -r "$APK"; then
    die "The app could not be installed. On Xiaomi phones enable 'Install via USB' in Developer options."
fi
ok "App installed"

# ---- 5. port 5555 ---------------------------------------------------------
info "[4/5] Opening ADB port 5555 on the phone..."
"$ADB" tcpip 5555 >/dev/null 2>&1
sleep 3
"$ADB" wait-for-device
PORT="$("$ADB" shell getprop service.adb.tcp.port | tr -d '\r')"
if [ "$PORT" = "5555" ]; then ok "Port 5555 is open"; else warn "The phone reports port '$PORT'. Run activate_port_5555.sh if the app cannot connect."; fi

# ---- 6. launch ------------------------------------------------------------
info "[5/5] Starting NeuroPad..."
"$ADB" shell am start -n "$PKG/.ui.MainActivity" --es neuropad_mode port5555 >/dev/null 2>&1 || true
ok "NeuroPad is open on the phone"

echo
echo -e "${G}==========================================================${N}"
echo -e "${Y}  Done!${N}"
echo -e "${G}==========================================================${N}"
echo " 1. Unplug the phone from the computer."
echo " 2. Plug your USB gamepad into the phone."
echo " 3. In NeuroPad, tap the big power button."
echo
echo " After a reboot of the phone, run activate_port_5555.sh again"
echo " (or pair NeuroPad with Wireless debugging once and forget the computer)."
echo
