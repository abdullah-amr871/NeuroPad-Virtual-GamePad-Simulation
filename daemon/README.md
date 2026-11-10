# uinput_proto

The native half of NeuroPad: a static ARM64 program that creates the virtual controller
through `/dev/uhid` and relays HID reports between the kernel and the app.

It reads commands as JSON lines on stdin and prints events as JSON lines on stdout; the
protocol is described in the header of [`uinput_proto.c`](uinput_proto.c) and in
[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md). It exits when stdin closes, which
destroys the virtual controller.

## Build

Only [Zig](https://ziglang.org/download/) is needed (it bundles a C compiler and musl):

```bash
zig cc -target aarch64-linux-musl -static -O2 -s -o uinput_proto uinput_proto.c
```

The result is a ~40 KB static binary with no dependency on Android's libc. The app ships a
prebuilt copy in `app/src/main/assets/uinput_proto`; replace that file to use your build.

## Test

The JSON helpers live in `json_mini.h` and can be tested on any machine:

```bash
cc -o test_json test_json.c && ./test_json
```

## Try it by hand

```bash
adb push uinput_proto /data/local/tmp/ && adb shell chmod 755 /data/local/tmp/uinput_proto
adb shell /data/local/tmp/uinput_proto --version
adb shell /data/local/tmp/uinput_proto -
```

Then paste a `register` line (see the protocol) and watch the controller appear in
`adb shell dumpsys input`. Errors are also written to `/data/local/tmp/uhid_err.log`.
