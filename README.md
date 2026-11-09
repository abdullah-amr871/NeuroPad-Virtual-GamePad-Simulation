# 🎮 NeuroPad - Virtual GamePad Simulation

[![Android Support](https://img.shields.io/badge/Android-8.0%20to%2016%2B-brightgreen.svg?logo=android&logoColor=white)](https://android.com)
[![Root Requirement](https://img.shields.io/badge/Root-Not%20Required%20(Rootless%20ADB)-blue.svg)](https://developer.android.com/tools/adb)
[![Kernel Subsystem](https://img.shields.io/badge/Kernel-UHID%20%2F%20evdev-orange.svg?logo=linux&logoColor=white)](https://www.kernel.org)
[![Latency](https://img.shields.io/badge/Haptic%20Latency-4ms%20--%2012ms-purple.svg)](#force-feedback--haptics-engine)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**NeuroPad** is a system-level virtual gamepad driver and controller emulation suite for Android. It bridges physical game controllers (connected via USB Host, Type-C, or Bluetooth) directly into the Android Linux kernel's User-space HID (`/dev/uhid`) subsystem.

This repository provides the **Universal 1-Click Installer** package, allowing anyone on **Windows, macOS, or Linux** to install NeuroPad, deploy the native kernel gamepad daemon (`uinput_proto`), and activate offline rootless loopback virtualization in seconds.

---

## ⚡ Why NeuroPad?

Many competitive mobile titles (such as *Call of Duty: Mobile*, *Warzone Mobile*, *Genshin Impact*, *Minecraft*, and console emulators) restrict controller gameplay exclusively to official Xbox or PlayStation hardware. Generic Bluetooth gamepads, USB-C telescopic grips (GameSir, Razer Kishi, Backbone), and custom arcade sticks are frequently rejected or lack haptic vibration.

NeuroPad solves this system-wide:
* **Zero Root Required:** Emulates an authentic controller using a local ADB loopback daemon (`adbd` on port `5555`). Root is fully supported as an optional fallback.
* **Authentic Hardware Descriptors:** Spawns native virtual controllers with legitimate Vendor ID (VID) and Product ID (PID) descriptors.
* **Sub-12ms Low-Latency Force Feedback:** Features a bi-directional haptics routing engine that reads kernel output reports (`FF_RUMBLE`) and actuates physical rumble motors with native responsiveness.
* **1-Click Plug & Play:** Automated installers bundle portable platform tools — no manual SDK configuration or command-line gymnastics required.

---

## 📥 Download

**[⬇️ Download the latest NeuroPad 1-Click Installer (ZIP)](https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation/releases/latest)**

1. Open the link above and download `NeuroPad_OneClick_Installer.zip` from the **Assets** list.
2. Extract the whole ZIP to a folder (do not run the installer from inside the ZIP).
3. Follow the Quick Start below.

Prefer Git? `git clone https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation.git`

---

## 🚀 Quick Start (1-Click Installation)

### Step 1: Enable USB Debugging on Your Android Phone
1. Open your phone's **Settings** ➔ **About Phone**.
2. Tap **Build Number** 7 times until you see the message *"You are now a developer!"*.
3. Go back to **Settings** ➔ **System** (or **Additional Settings**) ➔ **Developer Options**.
4. Enable **USB Debugging** (and on Xiaomi/MIUI/HyperOS, enable **USB Debugging (Security settings)**).

### Step 2: Connect Phone to PC
Plug your phone into your computer via a standard USB data cable. Unlock your phone screen and tap **Always allow from this computer** when the authorization prompt appears.

### Step 3: Run the 1-Click Installer

#### 🪟 Windows (Batch / CMD)
Double-click `Install_NeuroPad.bat`.

#### 🟦 Windows (PowerShell)
Right-click `Install_NeuroPad.ps1` ➔ **Run with PowerShell** (or run `powershell -ExecutionPolicy Bypass -File .\Install_NeuroPad.ps1`).

#### 🍏 macOS & 🐧 Linux
Open Terminal in the extracted folder and run:
```bash
chmod +x install_neuropad.sh
./install_neuropad.sh
```

### Step 4: Play!
1. Unplug the USB cable from your computer.
2. Connect your physical gamepad (USB-C cable or Bluetooth) to your phone.
3. Open the **NeuroPad** app and toggle **Gamepad Virtualization** to **ON**.

---

## 📦 Package Contents

| File / Folder | Target OS | Description |
| :--- | :--- | :--- |
| `Install_NeuroPad.bat` | Windows | 1-Click automated CMD installer. Bundles ADB detection, daemon push, port 5555 activation, and APK install. |
| `Install_NeuroPad.ps1` | Windows | PowerShell equivalent with rich color-coded status checks and hardware verification. |
| `install_neuropad.sh` | Linux / macOS | POSIX bash script for Unix systems. |
| `Activate_Port_5555.bat` | Windows | Fast utility to re-activate ADB TCP Port 5555 if you restart your phone. |
| `Activate_Port_5555.ps1` | Windows | PowerShell utility for Port 5555 reactivation. |
| `activate_port_5555.sh` | Linux / macOS | Shell utility for Port 5555 reactivation. |
| `NeuroPad_App.apk` | Android | Patched, aligned, and signed production build of the NeuroPad Virtual Gamepad application. |
| `uinput_proto` | Android (arm64-v8a) | Native User-space HID (`/dev/uhid`) kernel gamepad daemon binary. |
| `tools/` | Windows | Portable Android Debug Bridge (`adb.exe` + DLL dependencies) — zero installation needed on PC. |
| `src/daemon/` | Multi-platform | Full C source code (`uinput_proto.c`) and compilation guide for the native daemon. |
| `docs/` | All | Deep technical architectural specifications (`ARCHITECTURE.md`). |

---

## 🧠 System Architecture

NeuroPad registers a virtual hardware gamepad directly within the Linux kernel via `/dev/uhid`, which is then surfaced to Android's `InputReader` as a first-class physical controller.

```
+-------------------------------------------------------------------------+
|                           Android Application                           |
|      [MainActivity UI] <----------> [VirtualGamepadService (Engine)]    |
+-------------------------------------------------------------------------+
                                      |
                      (Local ADB Loopback :5555 OR Root su)
                                      |
                                      v
                  +---------------------------------------+
                  |   /data/local/tmp/uinput_proto        | (UID 2000 shell)
                  +---------------------------------------+
                                      |
                              (/dev/uhid ioctl)
                                      |
                                      v
                         +-------------------------+
                         |       Linux Kernel      |
                         |     (UHID / evdev)      |
                         +-------------------------+
                                      |
                            (Android InputReader)
                                      |
                                      v
                         +-------------------------+
                         |      Android Games      |
                         | (COD, Warzone, Emulators)|
                         +-------------------------+
```

---

## 🎮 Supported Gamepad Profiles

NeuroPad can emulate any of the following standard gaming hardware profiles:

| Mode | Emulated Device | VID | PID | Connection Protocol |
| :---: | :--- | :---: | :---: | :--- |
| **0** | Microsoft Xbox 360 Controller | `0x045E` | `0x028E` | USB Native |
| **1** | Microsoft Xbox Wireless Controller | `0x045E` | `0x02FD` | Bluetooth HID |
| **2** | Sony DualShock 4 (PS4) | `0x054C` | `0x05C4` | Bluetooth HID |
| **3** | Sony DualSense (PS5) | `0x054C` | `0x0CE6` | USB Native (`hid-playstation`) |
| **4** | Microsoft Xbox Series X/S Controller | `0x045E` | `0x0B13` | Bluetooth BLE |

---

## 📳 Force Feedback & Haptics Engine

NeuroPad features a dedicated 4-layer force feedback pipeline engineered to eliminate rumble motor lock-ups and deliver realistic vibration:

1. **Layer 1: Native Kernel UHID Output (4ms – 12ms latency)**  
   Intercepts `ioctl(EVIOCSFF)` rumble structs (`struct ff_effect` / `FF_RUMBLE`) from the Android kernel via `uinput_proto` stdout, actuating the physical gamepad motors in real time.
2. **Layer 2: Multi-Vendor System Property Override (Root Fallback)**  
   Activates `log.tag.VibratorService DEBUG` and OEM vibration hal tags on Samsung, Xiaomi, Pixel, and OnePlus devices to prevent suppression of haptic waveform calls.
3. **Layer 3: Intelligent Crossover Haptic Splitting (Logcat Fallback)**  
   Converts single-channel phone vibration into balanced dual-motor gamepad rumble:
   * **Low Vibration:** Weak high-frequency motor (right).
   * **Medium Vibration:** Proportional baseline with primary motor scaling (left).
   * **Heavy Vibration:** Full dual-motor amplitude with exponential smoothing.
4. **Layer 4: Built-in Watchdog Handler**  
   Automatically clears motor vibration if no fresh reports arrive within timeout intervals, guaranteeing motors never get stuck buzzing.

---

## ❓ Frequently Asked Questions (FAQ)

### Do I need to re-run the installer after rebooting my phone?
Android closes TCP port 5555 when the device completely reboots. If you reboot your phone:
1. Reconnect your phone via USB to your PC.
2. Double-click **`Activate_Port_5555.bat`** (takes only 2 seconds!).
3. Unplug USB and game on. You do **not** need to reinstall the APK or re-copy files.

### Does this require Root?
**No.** Root is completely optional. The installer leverages Android's built-in ADB loopback permission (`UID 2000 shell`), which possesses full read/write rights to `/dev/uhid`.

### The installer says `adb.exe could not be found`?
Make sure you extract the downloaded release ZIP file completely before running the installer. Keep the `tools` folder in the same directory as `Install_NeuroPad.bat`.

### The app says `HID Process: Disconnected`?
1. Verify that your phone is connected and authorized.
2. If using Port 5555, run `Activate_Port_5555.bat`.
3. If on MIUI / HyperOS, ensure **USB Debugging (Security Settings)** is toggled ON in Developer Options.

---

## 🛠️ Building the Kernel Daemon from Source

If you prefer to inspect and compile `uinput_proto` yourself:

```bash
# Clone the repository
git clone https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation.git
cd NeuroPad-Virtual-GamePad-Simulation/src/daemon

# Compile with Zig (static arm64 musl binary)
zig cc -target aarch64-linux-musl -static -O3 -o uinput_proto uinput_proto.c
```

For more details on internal drivers and architecture, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
All product and company names are trademarks™ or registered® trademarks of their respective holders. Use of them does not imply any affiliation with or endorsement by them.
