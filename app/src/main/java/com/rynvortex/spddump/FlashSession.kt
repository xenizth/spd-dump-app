package com.rynvortex.spddump

import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class BackendMode { NONE, USB_FD, ROOT }

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
        if (NativeBridge.available) {
            NativeBridge.nativeInit()
            NativeBridge.logCallback = { line -> appendLog(line) }
        } else {
            appendLog("Native USB backend not built yet - use root mode on the Device tab for now")
        }
    }

    fun appendLog(line: String) {
        val current = logLines.value.orEmpty()
        val trimmed = if (current.size > 2000) current.takeLast(1500) else current
        logLines.postValue(trimmed + line)
    }

    fun clearLog() = logLines.postValue(emptyList())

    fun attachUsbBackend(backend: UsbBackend) { usbBackend = backend }

    fun onUsbOpened(fd: Int, vendorId: Int, productId: Int) {
        if (!NativeBridge.available) {
            appendLog("Native USB backend not built yet - use root mode instead")
            return
        }
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
