# NeuroPad: Comprehensive Architecture & Design Walkthrough

This document compiles everything known about the **NeuroPad** Android application, detailing its systems architecture, input virtualization pipeline, loopback mechanisms, button mapping engine, and the redesigned force-feedback redirection stack.

---

## 1. High-Level Architecture Overview

NeuroPad is a system-level virtual gamepad driver for Android. It operates as a bridge between physical game controllers (connected via USB Host or Bluetooth) and the Android kernel's Input Subsystem, allowing users to emulate standard gamepads (Xbox 360, Xbox One, DualShock 4, DualSense, Xbox Series X/S) system-wide.

```
+-------------------------------------------------------------+
|                     Android Application                     |
|  [MainActivity UI] <-> [VirtualGamepadService (Kotlin/Java)]|
+-------------------------------------------------------------+
                               |
            (Root Shell 'su' OR Local ADB Loopback)
                               |
                               v
             +----------------------------------+
             |        /system/bin/hid           | (Shell/Root UID)
             +----------------------------------+
                               |
                        (/dev/uhid JNI)
                               |
                               v
                     +------------------+
                     |  Linux Kernel    |
                     |  (evdev/UHID)    |
                     +------------------+
                               |
                     (Android InputReader)
                               |
                               v
                     +------------------+
                     |  Android Games   |
                     +------------------+
```

---

## 2. Core Architectural Components

### A. MainActivity (UI & Controller Dashboard)
* **Purpose:** Serves as the user-facing configuration console and input monitor.
* **Key Features:**
  * Displays active emulation modes, layout settings (Default, Nintendo, Custom), and connection toggles.
  * Monitors real-time analog stick axes ($LX, LY, RX, RY$), D-pad directions, and button presses.
  * Displays real-time haptic motor intensities ($Left, Right$ rumble bars).
  * Houses the **hidden Developer Diagnostics screen** (triggered by tapping the *HID Process* status text view).

### B. VirtualGamepadService (Virtualization Controller)
* **Purpose:** Manages the lifecycle of the virtual controller.
* **Input Injection:** When physical inputs arrive, it formats them into a binary HID input report and writes it to the standard input (stdin) of `/system/bin/hid`.
* **Output Reading:** It reads `/system/bin/hid`'s standard output (stdout), which outputs JSON messages when the Android kernel transmits HID Output Reports (like rumble requests) back to the virtual device.
* **Fail-Safe Watchdog:** Implements `rumbleWatchdogHandler` to automatically reset rumble intensities to `0` if no fresh reports are received within a set timeout, preventing gamepad motors from getting stuck vibrating.

### C. The Local ADB / Root loopback
Registering virtual devices on Android requires write access to the kernel's userspace HID node (`/dev/uhid`). Regular Android applications do not have this permission. NeuroPad bypasses this security boundary using one of two methods:
1. **Root Mode (`su`):** If root is available, the app directly spawns `/system/bin/hid -` inside a superuser shell.
2. **ADB Mode (Rootless Loopback):** The app starts a dynamic loopback connection to the local Android Debug Bridge daemon (`adbd` on port `5555` or a dynamically discovered pairing port). Since the `shell` UID has `/dev/uhid` permissions, spawning `/system/bin/hid` over the local ADB stream successfully registers the virtual gamepad.

---

## 3. Emulation Modes & Protocols

NeuroPad supports five distinct controller configurations:

| Emulation Mode | Emulated Device | Connection Type | Vendor ID (VID) | Product ID (PID) |
| :--- | :--- | :--- | :--- | :--- |
| **0** | Xbox 360 Controller | USB | `0x045E` | `0x028E` |
| **1** | Xbox Wireless Controller | Bluetooth | `0x045E` | `0x02FD` |
| **2** | Sony DualShock 4 | Bluetooth | `0x054C` | `0x05C4` |
| **3** | Sony DualSense PS5 | USB | `0x054C` | `0x0CE6` |
| **4** | Xbox Wireless Controller | Bluetooth | `0x045E` | `0x0B13` |

---

## 4. Redesigned Haptics & Force Feedback Stack

The vibration engine operates in four prioritized layers, designed to optimize fidelity and latency depending on device capabilities:

### Layer 1: Native Force Feedback & HID Output Report Routing (Highest Priority)
* **Execution Path:**
  1. Game requests rumble via Android `InputDevice.getVibrator()`.
  2. AOSP compiles the command into a Linux force feedback rumble struct (`struct ff_effect` of type `FF_RUMBLE`) and calls `ioctl(fd, EVIOCSFF)` on the input node.
  3. The kernel HID driver (e.g. `hid-sony` or `hid-playstation`) translates the evdev event into a standard HID Output Report (Report ID 17/5 for DS4, Report ID 2 for DualSense).
  4. The report is written to `/dev/uhid`, read by `/system/bin/hid`, and written to stdout as JSON.
  5. NeuroPad's `VirtualGamepadService` intercepts this stdout report, extracts raw left/right motor values, and writes them directly to the physical controller.
* **Latency:** **4ms - 12ms** (Direct Kernel routing).

### Layer 2: System Property Activation (Root Fallback)
* **Execution Path:** During startup in Root Mode, `HapticRedirector` executes `setprop log.tag.VibratorService DEBUG` (and related `VibratorHal` / `Vibrator` tags).
* **Purpose:** Forces multi-vendor Android ROMs (Samsung, Xiaomi, Pixel, OnePlus) to log vibration command arguments (`OneShot` or `Waveform` objects) to the system Logcat buffer, bypassing OEM log-suppression rules.

### Layer 3: Haptic Splitting Crossover (Logcat Fallback)
* **Execution Path:** Used when native `EV_FF` is unavailable (e.g., rootless Xbox Bluetooth emulations). NeuroPad spawns a Logcat reader thread.
* **Splitting Algorithm:** The single-channel phone vibration amplitude (0-255) is split into a strong low-frequency (left) and weak high-frequency (right) dual-motor gamepad output:
  * **Low Vibration (< Threshold 1):** Routes exclusively to the right motor at scaled intensity.
  * **Medium Vibration (Threshold 1 to 2):** Runs the right motor at baseline, scales up the left motor proportionally.
  * **Heavy Rumble (> Threshold 2):** Drives both motors at full amplitude.
  * **Smoothing Filter:** Applies exponential decay smoothing ($left = (1 - \alpha) \cdot left + \alpha \cdot lastLeft$) to prevent sudden motor jumps during step transitions.
* **Latency:** **25ms - 50ms** (Logcat processing overhead).

### Layer 4: Real-time Developer Diagnostics & Stack Tracer
* **Diagnostic Screen:** Tapping the *HID Process* status text displays a diagnostic panel probing:
  * Active capability checks (Root status, ADB status, `/dev/uhid` / `/dev/uinput` writability).
  * EV_FF kernel support and `InputDevice.hasVibrator()` registration.
  * Running haptic backend, protocol, and real-time millisecond latency samples.
* **Input Stack Tracer:** Every haptic call logs a trace route:
  `[HapticTracer] Native HID Output Path: Game -> [Android HID Subsystem] -> UHID Output Received -> NeuroPad -> Physical Controller`
* **Exporting Reports:** Saves diagnostics reports to `neuropad_haptics_report.txt` in the app's external files directory for bug reproduction.
