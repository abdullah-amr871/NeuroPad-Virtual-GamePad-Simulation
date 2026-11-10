NeuroPad 2 - rumble for USB gamepads on Android
================================================

WHAT IS IN THIS FOLDER
  NeuroPad.apk              the Android app
  uinput_proto              the small driver the app runs on the phone
  Install_NeuroPad.bat      one-click installer for Windows
  Activate_Port_5555.bat    re-opens the port after the phone was rebooted
  install_neuropad.sh       the same for macOS / Linux
  activate_port_5555.sh
  tools\                    adb for Windows (nothing to install on the PC)

INSTALL (2 minutes)
  1. On the phone: Settings > About phone > tap "Build number" 7 times.
     Then Settings > System > Developer options > enable "USB debugging".
     (Xiaomi: also enable "Install via USB" and "USB debugging (Security settings)".)
  2. Connect the phone to the computer with a USB data cable and tap "Allow".
  3. Windows: double-click Install_NeuroPad.bat
     macOS / Linux: run  ./install_neuropad.sh  in a terminal
  4. Unplug the phone, plug the gamepad into it, open NeuroPad and tap the
     big power button.

AFTER A REBOOT OF THE PHONE
  Android closes port 5555. Connect the phone again and run
  Activate_Port_5555.bat (or activate_port_5555.sh). Nothing is reinstalled.

NO COMPUTER AT HAND? (Android 11 or newer)
  Install NeuroPad.apk directly on the phone, open the Connection tab,
  choose "Wireless debugging" and follow the pairing steps. The app then
  installs its driver by itself and never needs a computer.

UPGRADING FROM 1.0
  Version 2 is a new app (new name on the phone: "NeuroPad"). The installer
  removes the old "NEUROPAD (GamePad Virtualizer)" app for you.

HELP
  https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation
