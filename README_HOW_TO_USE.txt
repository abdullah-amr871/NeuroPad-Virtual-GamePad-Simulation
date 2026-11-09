========================================================================
         NEUROPAD - VIRTUAL GAMEPAD SIMULATION (INSTALLER GUIDE)
========================================================================

This package contains everything required to install and configure
NeuroPad - Virtual GamePad Simulation on ANY Android device (Android 8.0 - Android 16+).

------------------------------------------------------------------------
HOW TO INSTALL TO ANY ANDROID PHONE:
------------------------------------------------------------------------

1. Enable USB Debugging on the target phone:
   - Go to Settings -> About Phone -> Tap "Build Number" 7 times.
   - Go to Settings -> System -> Developer Options -> Enable "USB Debugging".

2. Plug the phone into your PC via USB cable.

3. Double-click "Install_NeuroPad.bat" (or right-click Install_NeuroPad.ps1 -> Run with PowerShell).

4. The installer will automatically:
   - Detect the connected Android phone.
   - Push the native uinput_proto gamepad kernel daemon to /data/local/tmp/.
   - Set executable permissions (chmod 755).
   - Activate ADB TCP Port 5555 for standalone gamepad virtualization.
   - Install NeuroPad_App.apk.
   - Launch NeuroPad on your phone screen.

5. UNPLUG the phone from your PC, attach your Gamepad (USB-C or Bluetooth),
   and turn Virtualization ON inside NeuroPad!

------------------------------------------------------------------------
PACKAGE CONTENTS:
------------------------------------------------------------------------
- Install_NeuroPad.bat       : 1-Click launcher script (Double-click this!)
- Install_NeuroPad.ps1       : Installer engine script
- NeuroPad_App.apk           : Patched, signed NeuroPad application
- uinput_proto               : Native arm64-v8a kernel gamepad daemon binary
- tools/                     : Portable ADB toolset (No PC setup required)
- README_HOW_TO_USE.txt      : Instructions guide

========================================================================
