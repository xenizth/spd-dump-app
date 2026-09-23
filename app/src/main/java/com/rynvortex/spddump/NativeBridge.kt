package com.rynvortex.spddump

/**
 * JNI bridge into libspddump.so (native-lib.cpp + spd_dump core + libusb).
 * See app/src/main/cpp/PATCHING.md for the two small hooks spd_dump.c needs
 * before this will link and behave correctly.
 */
object NativeBridge {
    init {
        System.loadLibrary("spddump")
    }

    /** Must be called once before anything else; sets up the libusb_context. */
    external fun nativeInit(): Boolean

    /**
     * Non-root path: wraps an already-permission-granted USB fd (from UsbBackend)
     * via libusb_wrap_sys_device instead of libusb_open(vid, pid).
     * Returns true on success.
     */
    external fun nativeOpenWithFd(fd: Int, vendorId: Int, productId: Int): Boolean

    /**
     * Runs an spd_dump command line, e.g.
     *   ["--wait","300","fdl",fdl1Path,"0x40004000","fdl",fdl2Path,"0x0","exec","reset"]
     * Log lines are delivered via the onNativeLog callback below (called from the
     * native thread - marshal to the UI thread on the Kotlin side).
     */
    external fun nativeRunCommands(args: Array<String>): Int

    external fun nativeClose()

    // Called from native-lib.cpp via JNI - keep this signature in sync with the C++ side.
    @JvmStatic
    var logCallback: ((String) -> Unit)? = null

    @JvmStatic
    fun onNativeLog(line: String) {
        logCallback?.invoke(line)
    }
}
