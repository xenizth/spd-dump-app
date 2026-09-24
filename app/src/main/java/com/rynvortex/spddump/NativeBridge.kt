package com.rynvortex.spddump

object NativeBridge {
    val available: Boolean = try {
        System.loadLibrary("spddump")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    external fun nativeInit(): Boolean
    external fun nativeOpenWithFd(fd: Int, vendorId: Int, productId: Int): Boolean
    external fun nativeRunCommands(args: Array<String>): Int
    external fun nativeClose()

    @JvmStatic
    var logCallback: ((String) -> Unit)? = null

    @JvmStatic
    fun onNativeLog(line: String) {
        logCallback?.invoke(line)
    }
}
