#!/usr/bin/env bash
# ========================================================================
#       NeuroPad - Virtual GamePad Simulation (Shell Installer)
#                    (Linux & macOS Compatible)
# ========================================================================
# Requires: USB Debugging enabled on Android device connected via USB
# ========================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ANSI color codes
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}==========================================================${NC}"
echo -e "${YELLOW}      NeuroPad - Virtual GamePad Simulation               ${GREEN}[v1.0]${NC}"
echo -e "${CYAN}==========================================================${NC}"
echo ""
echo "This script configures your Android phone for standalone"
echo "kernel-level gamepad virtualization without root."
echo ""
echo -e "${YELLOW}Please ensure:${NC}"
echo "  1. Your phone is connected to this machine via USB."
echo "  2. USB Debugging is ENABLED in Developer Options."
echo "  3. You tap 'Allow' on your phone when prompted."
echo ""

read -rp "Press ENTER to begin installation (or Ctrl+C to cancel)..." _
echo ""

# ------------------------------------------------------------------------
# 1. Locate ADB
# ------------------------------------------------------------------------
echo -e "${CYAN}Searching for adb...${NC}"
ADB_BIN=""

if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
elif [ -x "$SCRIPT_DIR/tools/adb" ]; then
    ADB_BIN="$SCRIPT_DIR/tools/adb"
elif [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
    ADB_BIN="$HOME/Android/Sdk/platform-tools/adb"
fi

if [ -z "$ADB_BIN" ]; then
    echo -e "${RED}[ERROR] adb binary could not be found!${NC}"
    echo "Please install Android platform-tools (e.g., 'brew install android-platform-tools' or 'sudo apt install adb')."
    exit 1
fi

echo -e "${GREEN}[OK] Found ADB: $ADB_BIN${NC}"
echo ""

# ------------------------------------------------------------------------
# 2. Wait for Device
# ------------------------------------------------------------------------
echo -e "${CYAN}[1/5] Waiting for connected Android device...${NC}"
"$ADB_BIN" wait-for-device
DEVICE_MODEL="$("$ADB_BIN" shell getprop ro.product.model | tr -d '\r')"
echo -e "${GREEN}[OK] Connected device: $DEVICE_MODEL${NC}"
echo ""

# ------------------------------------------------------------------------
# 3. Deploy uinput_proto Kernel Daemon
# ------------------------------------------------------------------------
echo -e "${CYAN}[2/5] Deploying native kernel gamepad daemon (uinput_proto)...${NC}"
UINPUT_BIN="$SCRIPT_DIR/uinput_proto"

if [ -f "$UINPUT_BIN" ]; then
    "$ADB_BIN" push "$UINPUT_BIN" /data/local/tmp/uinput_proto >/dev/null 2>&1
    "$ADB_BIN" shell chmod 755 /data/local/tmp/uinput_proto >/dev/null 2>&1
    IS_EXEC="$("$ADB_BIN" shell "[ -x /data/local/tmp/uinput_proto ] && echo YES || echo NO" | tr -d '\r')"
    if [ "$IS_EXEC" = "YES" ]; then
        echo -e "${GREEN}[OK] Gamepad daemon deployed to /data/local/tmp/uinput_proto (executable)${NC}"
    else
        echo -e "${YELLOW}[WARNING] Deployed but executable check returned: $IS_EXEC${NC}"
    fi
else
    echo -e "${YELLOW}[WARNING] uinput_proto binary not found in script folder!${NC}"
fi
echo ""

# ------------------------------------------------------------------------
# 4. Enable TCP/IP Port 5555 Loopback
# ------------------------------------------------------------------------
echo -e "${CYAN}[3/5] Enabling ADB TCP loopback on Port 5555...${NC}"
"$ADB_BIN" tcpip 5555
sleep 2
"$ADB_BIN" wait-for-device
TCP_PORT="$("$ADB_BIN" shell getprop service.adb.tcp.port | tr -d '\r')"
echo -e "${GREEN}[OK] ADB TCP Port active: $TCP_PORT${NC}"
echo ""

# ------------------------------------------------------------------------
# 5. Install APK
# ------------------------------------------------------------------------
echo -e "${CYAN}[4/5] Installing NeuroPad App (NeuroPad_App.apk)...${NC}"
APK_FILE="$SCRIPT_DIR/NeuroPad_App.apk"

if [ -f "$APK_FILE" ]; then
    "$ADB_BIN" install -r -d "$APK_FILE"
    echo -e "${GREEN}[OK] NeuroPad App installed successfully!${NC}"
else
    echo -e "${RED}[ERROR] NeuroPad_App.apk not found in script folder!${NC}"
fi
echo ""

# ------------------------------------------------------------------------
# 6. Launch App
# ------------------------------------------------------------------------
echo -e "${CYAN}[5/5] Launching NeuroPad on phone screen...${NC}"
"$ADB_BIN" shell am force-stop com.g8.driver >/dev/null 2>&1 || true
"$ADB_BIN" shell am start -n com.g8.driver/.MainActivity >/dev/null 2>&1 || true
echo -e "${GREEN}[OK] App launched on device!${NC}"
echo ""

# ------------------------------------------------------------------------
# Complete
# ------------------------------------------------------------------------
echo -e "${GREEN}==========================================================${NC}"
echo -e "${YELLOW}               INSTALLATION COMPLETE!                     ${NC}"
echo -e "${GREEN}==========================================================${NC}"
echo ""
echo -e "${CYAN}NEXT STEPS:${NC}"
echo " 1. Unplug the USB cable from your computer."
echo " 2. Plug your Gamepad (USB-C or Bluetooth) into your phone."
echo " 3. Toggle 'Gamepad Virtualization' ON inside NeuroPad!"
echo ""
