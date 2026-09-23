# Wiring in the real spd_dump source

This scaffold builds a working Android app *shape* — UI, USB permission flow,
root fallback, JNI bridge — but it can't compile until two things are dropped in,
because I don't have network access from this environment to pull them for you.

## 1. Get the pieces

- **spd_dump.c** (and its headers) from the upstream project — the same source
  the Termux release (`Seuj09/Spd_dump_termux`, itself built from
  `ilyakurdyukov/spreadtrum_flash`) is compiled from.
  Drop it into `third_party/spd_dump/`.
- **libusb-android prebuilt**, version >= 1.0.22 (needs `libusb_wrap_sys_device`,
  added in 1.0.22). Drop headers into `third_party/libusb/include/` and the
  arm64-v8a `.so` into `third_party/libusb/lib/arm64-v8a/libusb1.0.so`.

## 2. Two small patches to spd_dump.c

The upstream tool is written as a standalone CLI (`main(argc, argv)`) that opens
the device itself via `libusb_open_device_with_vid_pid()`. Two changes make it
usable from a library:

**a) Expose a callable entry point instead of `main()`:**

```c
// Rename main(int argc, char **argv) to:
int spd_dump_run(int argc, char **argv,
                  libusb_device_handle *preopened_handle,
                  void (*log_fn)(const char *line)) {
    // ... existing body ...
}
```

Replace its internal `printf`/`fprintf` calls with `log_fn(buf)` (snprintf into
a buffer first) so output reaches the Kotlin log view instead of stdout, which
doesn't go anywhere useful inside an Android process.

**b) Accept a pre-opened handle for the non-root path:**

Find where it currently does something like:

```c
handle = libusb_open_device_with_vid_pid(ctx, VID, PID);
```

and change it to:

```c
if (preopened_handle) {
    handle = preopened_handle;   // came from UsbManager via nativeOpenWithFd
} else {
    handle = libusb_open_device_with_vid_pid(ctx, VID, PID); // root path
}
```

That's the entire non-root trick: everything else in spd_dump's protocol logic
(FDL loading, partition read/write, the diag handshake) is unchanged — it just
talks to a `libusb_device_handle` it doesn't care how it got.

## 3. Build

Once both are in place: `./gradlew assembleDebug`. Output APK lands in
`app/build/outputs/apk/debug/`.

## Notes / gotchas

- `libusb_wrap_sys_device` needs Android's USB permission already granted for
  that specific device — that's what `UsbBackend.kt`'s permission dialog is for.
  It does **not** need root, which is the whole point.
- Some phones' kernels restrict raw bulk transfer sizes over the USB Host API
  fd path more than they do for a rooted `/dev/bus/usb` open — if large
  `read_flash`/`write_flash` transfers stall on the non-root path, chunk them
  smaller in spd_dump's transfer loop.
- The root path re-uses the same binary logic but skips the fd dance and lets
  spd_dump open the device the normal way, running under `su`. Keep both code
  paths since some ROMs' USB Host API implementations are flaky and root is a
  useful fallback on devices you already control.
- Update `device_filter.xml` with any additional VID:PID pairs you see in
  `dmesg`/`logcat` for the specific SoCs you're targeting (T606 on the X6525
  should attach as 1782:4d00 in most diag stages, but double check).
