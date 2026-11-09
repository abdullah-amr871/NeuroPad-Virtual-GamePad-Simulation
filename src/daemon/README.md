# uinput_proto — native UHID gamepad daemon

`uinput_proto.c` is the native daemon the installer pushes to `/data/local/tmp/uinput_proto`.
It opens `/dev/uhid`, registers a virtual controller from the app's HID descriptor, injects input
reports from stdin, and writes force-feedback output reports to stdout.

## Build (static arm64 binary)

Install [Zig](https://ziglang.org/download/), then:

```bash
zig cc -target aarch64-linux-musl -static -O3 -o uinput_proto uinput_proto.c
```

Copy the resulting `uinput_proto` to the repository root to replace the prebuilt binary.
