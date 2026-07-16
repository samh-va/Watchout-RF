package com.watchoutrf.app.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages USB SDR device detection, permission requests, and connection lifecycle.
 * Uses Android USB Host API to communicate with RTL-SDR dongles.
 */
@Singleton
class UsbDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Disconnected)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    companion object {
        private const val TAG = "UsbDeviceManager"
        const val ACTION_USB_PERMISSION = "com.watchoutrf.app.USB_PERMISSION"

        // Known RTL-SDR vendor/product IDs
        private val SUPPORTED_DEVICES = listOf(
            Pair(0x0BDA, 0x2832), // RTL2832U generic
            Pair(0x0BDA, 0x2838), // RTL-SDR Blog V3/V4
            Pair(0x0BDA, 0x2828), // RTL2832U with FC0012
            Pair(0x0BDA, 0x2829), // RTL2832U with FC0013
            Pair(0x0BDA, 0x2830), // RTL2832U with FC2580
            Pair(0x0BDA, 0x2831), // RTL2832U with E4000
        )
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                        if (device != null) {
                            val deviceInfo = device.toDeviceInfo()
                            if (granted) {
                                Log.i(TAG, "USB permission granted for ${deviceInfo.displayName}")
                                _connectionState.value = UsbConnectionState.Connected(deviceInfo)
                            } else {
                                Log.w(TAG, "USB permission denied for ${deviceInfo.displayName}")
                                _connectionState.value = UsbConnectionState.PermissionDenied(deviceInfo)
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i(TAG, "USB device attached")
                    scanForDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB device detached")
                    _connectionState.value = UsbConnectionState.Disconnected
                }
            }
        }
    }

    /**
     * Start listening for USB device events.
     * Must be called when the app starts (e.g., from MainActivity.onCreate).
     */
    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        Log.i(TAG, "USB monitoring started")
        // Check if a device is already connected
        scanForDevices()
    }

    /**
     * Stop listening for USB device events.
     */
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(usbReceiver)
            Log.i(TAG, "USB monitoring stopped")
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            Log.w(TAG, "Receiver was not registered")
        }
    }

    /**
     * Scan for already-connected SDR devices and request permission if found.
     */
    fun scanForDevices() {
        val deviceList = usbManager.deviceList
        Log.i(TAG, "Scanning USB devices: found ${deviceList.size} device(s)")

        for ((_, device) in deviceList) {
            if (isSupportedDevice(device)) {
                Log.i(TAG, "Found supported SDR device: ${device.deviceName} " +
                        "(VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)})")

                if (usbManager.hasPermission(device)) {
                    val deviceInfo = device.toDeviceInfo()
                    _connectionState.value = UsbConnectionState.Connected(deviceInfo)
                } else {
                    val deviceInfo = device.toDeviceInfo()
                    _connectionState.value = UsbConnectionState.PermissionRequested(deviceInfo)
                    requestPermission(device)
                }
                return // Handle first found device
            }
        }

        // No supported device found
        if (_connectionState.value !is UsbConnectionState.Disconnected) {
            _connectionState.value = UsbConnectionState.Disconnected
        }
    }

    /**
     * Request USB permission for the given device.
     */
    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
        Log.i(TAG, "Permission requested for ${device.deviceName}")
    }

    /**
     * Get the native file descriptor for the connected device.
     * Returns -1 if no device is connected or permission not granted.
     */
    fun getDeviceFileDescriptor(): Int {
        val state = _connectionState.value
        if (state !is UsbConnectionState.Connected) return -1

        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            if (isSupportedDevice(device) && usbManager.hasPermission(device)) {
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    return connection.fileDescriptor
                }
            }
        }
        return -1
    }

    fun isSupportedDevice(device: UsbDevice): Boolean {
        return SUPPORTED_DEVICES.any { (vid, pid) ->
            device.vendorId == vid && device.productId == pid
        }
    }

    private fun UsbDevice.toDeviceInfo(): UsbDeviceInfo {
        return UsbDeviceInfo(
            deviceName = deviceName,
            vendorId = vendorId,
            productId = productId,
            manufacturerName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) manufacturerName else null,
            productName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) productName else null,
            serialNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) serialNumber else null,
        )
    }
}
