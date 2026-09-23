# Spd Dump App

Android app scaffold wrapping `spd_dump` (Spreadtrum/Unisoc flash tool) so it
runs as a real app with a GUI, on both rooted and non-rooted phones acting as
the USB host.

- **Non-root**: Android USB Host API grants per-device permission, we pull a
  raw fd off the connection, and hand it to libusb via `libusb_wrap_sys_device`
  (libusb >= 1.0.22) instead of opening `/dev/bus/usb` directly.
- **Root**: falls back to shelling out to the bundled binary via `su`, opening
  the device the normal way.

See `app/src/main/cpp/PATCHING.md` for the two source pieces you need to drop
in before this compiles (spd_dump.c itself + a libusb-android prebuilt) and
the two small patches spd_dump.c needs to support the pre-opened-handle path.

## Layout
- `app/src/main/java/.../UsbBackend.kt` — non-root device detection + permission flow
- `app/src/main/java/.../RootBackend.kt` — root fallback, shells to su
- `app/src/main/java/.../NativeBridge.kt` — JNI declarations
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, wraps the fd, forwards logs
- `app/src/main/cpp/CMakeLists.txt` — build wiring for libusb + spd_dump.c
- `app/src/main/res/xml/device_filter.xml` — USB VID/PID filter (1782:4d00 by default)
