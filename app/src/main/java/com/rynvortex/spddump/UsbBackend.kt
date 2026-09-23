package com.rynvortex.spddump

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * Non-root USB backend.
 *
 * The whole trick that lets this run without root: Android's UsbManager grants
 * per-app access to a specific USB device via a permission dialog, and once granted
 * we can pull a raw file descriptor off UsbDeviceConnection. That fd is handed to
 * libusb_wrap_sys_device() on the native side instead of libusb_open(), so libusb
 * never needs to touch /dev/bus/usb directly (which is what actually requires root).
 */
class UsbBackend(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onDeviceFound(device: UsbDevice)
        fun onPermissionGranted(fd: Int, vendorId: Int, productId: Int)
        fun onPermissionDenied()
        fun onLog(line: String)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ACTION_USB_PERMISSION = "com.rynvortex.spddump.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            synchronized(this) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted && device != null) {
                    openDevice(device)
                } else {
                    listener.onLog("USB permission denied by user")
                    listener.onPermissionDenied()
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: IllegalArgumentException) {
            // wasn't registered - fine
        }
    }

    /** Look for a currently attached Spreadtrum-vendor device (VID 0x1782). */
    fun findCandidateDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { it.vendorId == 0x1782 }
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            usbManager.requestPermission(device, pi)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            listener.onLog("openDevice() returned null - device may have been unplugged")
            listener.onPermissionDenied()
            return
        }
        val fd = connection.fileDescriptor
        if (fd < 0) {
            listener.onLog("Got a device connection but no valid fd")
            listener.onPermissionDenied()
            return
        }
        listener.onLog("Got fd=$fd for ${device.vendorId.toString(16)}:${device.productId.toString(16)}")
        listener.onPermissionGranted(fd, device.vendorId, device.productId)
        // NOTE: don't call connection.close() while the native side is still using the fd -
        // libusb_wrap_sys_device takes ownership semantics that assume it stays open.
    }
}
