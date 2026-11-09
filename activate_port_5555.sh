#!/usr/bin/env bash
# ========================================================================
#            NeuroPad - Port 5555 Activator (Shell Script)
# ========================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}=======================================================${NC}"
echo -e "${YELLOW}           NEUROPAD - Port 5555 Activator             ${NC}"
echo -e "${CYAN}=======================================================${NC}"
echo ""
echo "This script configures your phone to accept local ADB"
echo "connections on Port 5555 so virtualization works offline."
echo ""
echo -e "${YELLOW}Please ensure:${NC}"
echo " 1. Your phone is connected via USB."
echo " 2. USB Debugging is ENABLED in Developer Options."
echo " 3. USB Debugging authorization is allowed on your screen."
echo ""

read -rp "Press ENTER to continue..." _
echo ""

ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
elif [ -x "$SCRIPT_DIR/tools/adb" ]; then
    ADB_BIN="$SCRIPT_DIR/tools/adb"
fi

if [ -z "$ADB_BIN" ]; then
    echo -e "${RED}[ERROR] adb binary could not be found!${NC}"
    exit 1
fi

echo -e "${CYAN}Checking connected devices...${NC}"
"$ADB_BIN" devices
echo ""

UINPUT_BIN="$SCRIPT_DIR/uinput_proto"
if [ -f "$UINPUT_BIN" ]; then
    echo -e "${CYAN}Deploying gamepad daemon (uinput_proto)...${NC}"
    "$ADB_BIN" push "$UINPUT_BIN" /data/local/tmp/uinput_proto >/dev/null 2>&1
    "$ADB_BIN" shell chmod 755 /data/local/tmp/uinput_proto >/dev/null 2>&1
    echo -e "${GREEN}[OK] Gamepad daemon deployed.${NC}"
fi

echo -e "${CYAN}Activating TCP/IP Port 5555...${NC}"
if "$ADB_BIN" tcpip 5555; then
    echo ""
    echo -e "${GREEN}=======================================================${NC}"
    echo -e "${YELLOW}SUCCESS! Port 5555 has been activated on your phone.${NC}"
    echo -e "${GREEN}=======================================================${NC}"
    echo ""
    echo -e "${CYAN}You can now:${NC}"
    echo " 1. Unplug the USB cable."
    echo " 2. Plug your Gamepad into your phone."
    echo " 3. Open NeuroPad and toggle Virtualization ON!"
else
    echo -e "${RED}[ERROR] Failed to set port 5555. Check USB connection and debugging authorization.${NC}"
    exit 1
fi
echo ""
