# NeuroPad architecture

NeuroPad has two halves: an Android app that owns the USB gamepad, and a tiny native
program (`uinput_proto`) that owns the virtual controller. They talk over a pipe.

```
                         app process (normal app uid)                    shell uid / root
 +-------------+   USB   +-------------------------------------+  JSON   +--------------+   /dev/uhid   +--------+
 | USB gamepad | <-----> | UsbGamepadManager -> BridgeSession  | <-----> | uinput_proto | <-----------> | kernel |
 +-------------+  XInput | XInputParser, ReportBuilder,        |  lines  +--------------+               +--------+
                         | RumbleParser, PhoneVibrationMirror  |                                             |
                         +-------------------------------------+                                    InputReader, games
```

## Why a separate program?

Creating an input device needs write access to `/dev/uhid`. On Android that is granted to
the `shell` user (uid 2000) and to root, never to an installed app. So the app obtains a
shell in one of three ways and runs the driver there:

| Mode | Transport | Class |
| :--- | :--- | :--- |
| Wireless debugging | ADB over TLS to `127.0.0.1:<random port>`, found through mDNS, paired with a six digit code | `AdbConnectionManager`, `AdbDiscovery`, `AdbPairingService` |
| PC port 5555 | ADB to `127.0.0.1:5555` after `adb tcpip 5555` from a computer | `AdbConnectionManager` |
| Root | `su` | `Shell` |

`Shell` hides the difference. `DaemonInstaller` makes sure `/data/local/tmp/uinput_proto`
matches the copy bundled in the APK (compared by size) and installs it if needed.

## The driver protocol

One JSON object per line, in both directions. It is a superset of what AOSP's
`/system/bin/hid` understands.

App to driver:

```json
{"id":1,"command":"register","name":"Xbox Wireless Controller","vid":1118,"pid":765,"bus":"bluetooth","descriptor":[5,1,9,5]}
{"id":1,"command":"report","report":[1,128,128,128,128,0,0,8,0,0]}
{"command":"get_report_reply","id":7,"err":0,"report":[5,30,0]}
{"command":"set_report_reply","id":8,"err":0}
```

Driver to app:

```json
{"event":"ready","version":"2.0.0"}
{"event":"registered"}
{"event":"error","message":"cannot open /dev/uhid: Permission denied"}
{"event":"output","id":1,"reportData":[3,15,0,0,83,58,255,0,0,0,0,0,0,0,0,0]}
{"event":"get_report","id":7,"reportId":5,"reportType":0}
{"event":"set_report","id":8,"reportId":2,"reportType":1,"reportData":[2,255]}
```

The app first tries ADB's raw `exec:` service (no terminal in between). If the driver
does not greet it, it falls back to the terminal-backed `shell:` service that 1.0 used;
a 1.0 driver, which prints no greeting, is still accepted.

## Input path

1. `UsbGamepadManager` claims the XInput interface (class `0xFF`, subclass `0x5D`,
   protocol `1`) with `force = true` and polls its interrupt IN endpoint on a
   high-priority thread.
2. `XInputParser` turns the 20-byte Xbox 360 packet into a `PadState`: sticks and triggers
   as 0..255, the D-pad as a hat value, and 16 logical buttons.
3. `ReportBuilder` applies the button layout and the Select + Start shortcut, then
   produces the input report of the selected profile:

   | Profile | Report | Layout |
   | :--- | :--- | :--- |
   | Xbox 360 / One / Series | 10 bytes | id, LX, LY, RX, RY, LT, RT, hat, buttons (2 bytes) |
   | DualShock 4 | 10 bytes | same axes, Sony button order |
   | DualSense | 64 bytes | axes, sequence counter, hat + buttons (3 bytes), zero padding |

4. The report is written to the driver as one `report` line; the driver forwards it to the
   kernel as `UHID_INPUT`.

The driver patches the Microsoft descriptors on the fly so that Android maps the right
stick to Z/Rz and the triggers to Brake/Gas, and it clears the digital LT/RT bits because
the analog axes already carry them.

## Rumble path

A game calls `Vibrator` on the virtual controller. Android writes a force-feedback effect
to the kernel HID driver, which emits an output report. The driver passes it to the app as
an `output` event and `RumbleParser` decodes it:

| Profile | Report id | Left (heavy) | Right (light) |
| :--- | :---: | :--- | :--- |
| DualSense | `0x02` | byte 4 | byte 3 |
| DualShock 4 | `0x11` / `0x05` | byte 7 / 5 | byte 6 / 4 |
| Xbox One / Series | `0x03` | byte 4 (0..100) | byte 5 (0..100), plus trigger motors in bytes 2 and 3 |
| Xbox 360 | none | byte 3 or 1 | byte 4 or 2 |

The values are multiplied by the user's gain and written to the pad as the Xbox 360 rumble
packet `00 08 00 LL RR 00 00 00`. A guard timer switches the motors off if nothing arrives
for a while (5 s by default), in case a stop report gets lost.

### Phone-vibration fallback

Some games never use controller rumble and only vibrate the phone. `PhoneVibrationMirror`
follows `logcat` for the system vibrator's log lines through the same shell,
`VibrationLogParser` extracts one-shot and waveform effects (and RichTap calls on phones
that use it), and `HapticSplitter` turns the single amplitude into two motor values:

- quiet effects: light motor only,
- medium effects: light motor plus a fading-in heavy motor,
- strong effects: both motors.

Vibrations requested by the system UI or the keyboard are ignored, and the whole fallback
pauses for the session once real controller rumble has been seen.

## App structure

```
app/src/main/java/io/github/abdullahamr871/neuropad/
  core/      pure Kotlin, no Android: profiles, descriptors, parsers, protocol (unit tested)
  usb/       UsbGamepadManager
  adb/       ADB client, mDNS discovery, pairing service
  bridge/    GamepadBridgeService (foreground), BridgeSession, Shell, DaemonInstaller,
             Settings, BridgeState (StateFlows observed by the UI), Quick Settings tile
  haptics/   PhoneVibrationMirror, Diagnostics
  ui/        Compose screens and AppViewModel
```

`BridgeState` is the single source of truth for the UI: status, ADB state, the live pad
state, rumble levels, counters and the activity log.

## Testing

- `app/src/test/.../core` covers the input path, the rumble path and the protocol.
- `ScreenshotTest` renders all four screens on the JVM with Robolectric and Roborazzi
  (`./gradlew :app:testDebugUnitTest -Pscreenshots`), which also produces the images in
  `docs/screenshots`.
- `daemon/test_json.c` tests the driver's JSON helpers on any host.

What cannot be tested without hardware: the USB transfer itself, the ADB transports and
the behaviour of each phone's kernel HID drivers.
