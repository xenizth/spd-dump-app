package com.rynvortex.spddump

import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class BackendMode { NONE, USB_FD, ROOT }

/**
 * App-wide session shared by all four tabs (Device / Operations / Files / Console),
 * so "run this operation" from the Operations grid can reuse whatever backend
 * was set up on the Device tab, and Console sees the same log stream.
 */
object FlashSession {

    val backendMode = MutableLiveData(BackendMode.NONE)
    val connectedDevice = MutableLiveData<UsbDevice?>(null)
    val logLines = MutableLiveData<List<String>>(emptyList())
    val busy = MutableLiveData(false)

    private var usbBackend: UsbBackend? = null
    private var rootBackend: RootBackend? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        NativeBridge.nativeInit()
        NativeBridge.logCallback = { line -> appendLog(line) }
    }

    fun appendLog(line: String) {
        val current = logLines.value.orEmpty()
        // keep the console from growing unbounded across a long session
        val trimmed = if (current.size > 2000) current.takeLast(1500) else current
        logLines.postValue(trimmed + line)
    }

    fun clearLog() = logLines.postValue(emptyList())

    fun attachUsbBackend(backend: UsbBackend) { usbBackend = backend }

    fun onUsbOpened(fd: Int, vendorId: Int, productId: Int) {
        val ok = NativeBridge.nativeOpenWithFd(fd, vendorId, productId)
        backendMode.postValue(if (ok) BackendMode.USB_FD else BackendMode.NONE)
    }

    fun setupRoot(context: Context): Boolean {
        val binaryPath = RootBackend.extractBinary(context)
        val backend = RootBackend(binaryPath, object : RootBackend.Listener {
            override fun onLog(line: String) = appendLog(line)
            override fun onFinished(exitCode: Int) {
                appendLog("[exit $exitCode]")
                busy.postValue(false)
            }
        })
        return if (backend.isRootAvailable()) {
            rootBackend = backend
            backendMode.postValue(BackendMode.ROOT)
            true
        } else {
            appendLog("Root not available")
            false
        }
    }

    /**
     * Runs an operation's spd_dump args on whichever backend is active.
     * See Operation.kt / OperationCatalog.kt for the argument templates -
     * these need to match the actual spd_dump build's command syntax.
     */
    fun runArgs(args: List<String>) {
        if (busy.value == true) {
            appendLog("Already busy with another command")
            return
        }
        appendLog("\$ " + args.joinToString(" "))
        busy.postValue(true)
        when (backendMode.value) {
            BackendMode.ROOT -> rootBackend?.run(args)
            BackendMode.USB_FD -> Thread {
                val code = NativeBridge.nativeRunCommands(args.toTypedArray())
                appendLog("[exit $code]")
                busy.postValue(false)
            }.start()
            else -> {
                appendLog("No backend connected - go to Device tab first")
                busy.postValue(false)
            }
        }
    }

    fun statusText(): LiveData<BackendMode> = backendMode
}
