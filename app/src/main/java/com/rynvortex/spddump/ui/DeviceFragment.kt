package com.rynvortex.spddump.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rynvortex.spddump.BackendMode
import com.rynvortex.spddump.FlashSession
import com.rynvortex.spddump.UsbBackend
import com.rynvortex.spddump.databinding.FragmentDeviceBinding

class DeviceFragment : Fragment(), UsbBackend.Listener {

    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!
    private lateinit var usbBackend: UsbBackend

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        usbBackend = UsbBackend(requireContext(), this)
        FlashSession.attachUsbBackend(usbBackend)

        FlashSession.backendMode.observe(viewLifecycleOwner) { mode ->
            binding.deviceStatusText.text = when (mode) {
                BackendMode.USB_FD -> "Connected via USB Host API (non-root)"
                BackendMode.ROOT -> "Connected via root backend"
                else -> "No backend connected"
            }
        }

        binding.connectButton.setOnClickListener {
            if (binding.rootModeSwitch.isChecked) {
                FlashSession.setupRoot(requireContext())
            } else {
                val device = usbBackend.findCandidateDevice()
                if (device == null) {
                    FlashSession.appendLog("No Spreadtrum (VID 0x1782) device found. Plug in via OTG and hold the boot key.")
                } else {
                    usbBackend.requestPermission(device)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        usbBackend.register()
    }

    override fun onStop() {
        super.onStop()
        usbBackend.unregister()
    }

    // ---- UsbBackend.Listener ----
    override fun onDeviceFound(device: android.hardware.usb.UsbDevice) {
        FlashSession.connectedDevice.postValue(device)
    }

    override fun onPermissionGranted(fd: Int, vendorId: Int, productId: Int) {
        FlashSession.onUsbOpened(fd, vendorId, productId)
    }

    override fun onPermissionDenied() {
        FlashSession.appendLog("USB permission denied")
    }

    override fun onLog(line: String) = FlashSession.appendLog(line)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
