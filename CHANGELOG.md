# Changelog

## 2.0.0

A complete rewrite. Version 1.0 was distributed as a patched binary; 2.0 is built from the
source in this repository. Every feature of 1.0 is still there.

Version 2 installs as a new app (`io.github.abdullahamr871.neuropad`). The installer removes
the 1.0 app (`com.g8.driver`); settings are not carried over.

> 2.0.0 was verified with unit tests and JVM-rendered screens. It had not been run on a
> physical phone with a gamepad at the time of release.

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
