# Changelog

## 2.0.4

Confirmed on a Nothing Phone (2) with the GameSir G8 Plus (MFi).

### Added

- **Rumble on the phone too** (Settings > Rumble). Plays game rumble on the phone's own
  vibration motor as well, for gamepads without rumble motors
  ([#8](https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation/issues/8)).
  It pauses itself while Android's "Vibrate input devices" is on, because Android would
  send that vibration straight back to the virtual controller.

### Fixed

- The README listed the 8BitDo Ultimate Mobile as a controller with rumble. It has no rumble
  motors and connects over Bluetooth only, so it was removed
  ([#8](https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation/issues/8)).

## 2.0.3

### Added

- **Support tab.** A new tab with Ko-fi and PayPal donation links, what donations pay for,
  and free ways to help (starring the repo, reporting your controller). A small card on the
  Home screen leads to it.

### Fixed

- **Bottom bar labels wrapped onto two lines** (for example "Connection") on narrow phones.
  Labels now always stay on one line.

## 2.0.2

### Fixed

- **Installer stopped with "more than one device/emulator".** When the phone was listed
  twice (USB and Wi-Fi ADB) or an emulator was running, `adb` refused to pick one and the
  installer reported a confusing empty phone state. The installer and the port activator
  now choose the USB device themselves and wait for the "Allow" prompt.

## 2.0.1

First version that was run on real hardware: a Nothing Phone (2) on Android 16 with a GameSir
G8 Plus (MFi). Input, the virtual controller and rumble were confirmed there.

### Fixed

- **Launcher taps, keyboard clicks and notifications rumbled the gamepad.** With "vibrate
  input devices" enabled (the default on Nothing phones) Android forwards every phone
  vibration to a connected controller. 1.0 muted the system ones; 2.0.0 dropped that by
  mistake. It is back, now based on the caller named in the vibration log, and it expires by
  itself instead of sticking until another app vibrates.
- **Weak, late rumble from short effects.** Games send ticks of 10-20 ms that a phone motor
  can play but a gamepad motor cannot. Pulses are now stretched to at least 80 ms.
- **Stuttering during long rumbles.** Long effects arrive as ~100 ms pulses separated by
  gaps of a few milliseconds. Those gaps are bridged instead of being passed to the motors.
- Vibration requests are now recognised on Android 13+ (`VibratorManagerService` lines).

### Changed

- **The bridge follows the gamepad.** It stops by itself when the gamepad is unplugged, so
  no ghost controller stays visible to games, and it refuses to start while none is
  connected. Optionally it also starts by itself when the gamepad is plugged in.
- **Adaptive layout.** Every screen uses two panes from 600 dp of width (landscape,
  foldables, tablets) and a single column otherwise.

### New

- Settings > Controls > **Start when the gamepad is plugged in**.
- Settings > Rumble > **Shortest rumble** (Off, 40, 80, 120, 200 ms).
- Settings > Rumble > **Ignore system vibration**.
- The app turns to landscape while a gamepad is plugged in, with the navigation on a side
  rail, so it can be used with the phone sitting in the grip.

## 2.0.0

A complete rewrite. Version 1.0 was distributed as a patched binary; 2.0 is built from the
source in this repository. Every feature of 1.0 is still there.

Version 2 installs as a new app (`io.github.abdullahamr871.neuropad`). The installer removes
the 1.0 app (`com.g8.driver`); settings are not carried over.

> 2.0.0 was verified with unit tests and JVM-rendered screens only. Use 2.0.1 or newer.

### New

- New interface in Jetpack Compose: Home, Controller, Connection and Settings tabs.
- Live view of the controller (sticks, triggers, buttons) and rumble meters.
- Rumble test buttons that drive the motors directly, without a game.
- The app installs its own driver over ADB, so Wireless debugging users no longer need a
  computer at all.
- Adjustable rumble strength (1.0 had a fixed 4x gain).
- Adjustable stuck-motor guard, fallback thresholds and smoothing (the settings existed in
  1.0 but had no screen).
- Switch for the Select + Start = Home shortcut.
- Quick Settings tile to start and stop the bridge.
- Automatic reconnect (three attempts) when the link to the driver drops.
- Diagnostics checklist and a report that can be shared from the app.
- New name on the launcher ("NeuroPad"), new icon.

### Fixed

- **Long rumbles were cut after 0.5 s.** The watchdog stopped the motors 500 ms after the
  last report, but the kernel only reports when an effect starts or stops. The guard is now
  5 s by default and can be changed or turned off.
- **Rumble went silent after any system vibration.** A keyboard click or notification muted
  controller rumble until some app vibrated the phone again. System haptics now only mute
  the phone-vibration fallback.
- **The fallback replayed old vibrations at start.** The log reader started at the beginning
  of the log buffer instead of "now".
- **Possible crash on Android 14** when asking for USB permission (mutable PendingIntent on
  an implicit intent).
- **Phantom M2 paddle.** Every press of A was also read as the M2 back paddle.
- **Mice and keyboards could be grabbed.** Any USB device from a list of vendors (Logitech,
  Razer, ...) was treated as a gamepad. Those now need a vendor-specific interface.
- **Wrong Wireless debugging port.** Ports announced by other phones on the same Wi-Fi were
  picked up. Only services of this device are used now.
- **Driver errors were reported as success.** The app sent `error`, the driver read `err`.
- **Driver could spin at 100 % CPU** after the app went away (hang-up on stdin without
  readable data).
- **SET_REPORT requests were never answered**, leaving the kernel driver waiting five
  seconds each time.
- A waveform line with timing and amplitude lists of different length killed the fallback.
- Inverted fallback thresholds could divide by zero.
- A stuck button could remain pressed on the virtual pad after unplugging the gamepad.
- The wrong USB interface could be chosen on composite devices (the last one with any
  endpoint instead of the first with an input endpoint).

### Removed

- **Deleting `/dev/input/event*` nodes (root mode).** 1.0 removed every input node that
  looked like a gamepad to hide the physical pad. That could take unrelated devices away
  until the next reboot. Claiming the USB interface already detaches the kernel driver.
- The root-only sysfs unbind and EVIOCGRAB code paths, for the same reason and because they
  relied on hidden Android APIs.
- The alarm-based service restart, which Android 12+ rejects. The foreground service is
  sticky instead.
- Unused permissions: storage, phone state and boot completed.
- The gyro settings stub. It was never functional in 1.0.

### Technical

- Kotlin, single-activity Compose app, minSdk 24, targetSdk 34.
- Pure logic (XInput parsing, report building, rumble parsing, haptic splitting, log parsing,
  driver protocol) is separated from Android and covered by unit tests.
- HID descriptors are byte-identical to 1.0.
- The driver greets the app with its version and confirms device creation, so failures
  produce a real error message.
- The driver shrank from 2.8 MB to 40 KB.
- The release build is no longer debuggable and is signed with a dedicated release key.
- GitHub Actions builds the app and runs the tests on every push.

## 1.0.0

First public release: patched build of the original app with the one-click installer.
