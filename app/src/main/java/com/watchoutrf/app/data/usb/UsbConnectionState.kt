package com.watchoutrf.app.data.usb

/**
 * Information about a connected USB SDR device.
 */
data class UsbDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val fileDescriptor: Int = -1,
) {
    /**
     * Returns a human-readable name for the device based on known VID/PID combinations.
     */
    val displayName: String
        get() = when {
            vendorId == 0x0BDA && productId == 0x2838 -> "RTL-SDR Blog V3/V4"
            vendorId == 0x0BDA && productId == 0x2832 -> "RTL2832U Generic"
            vendorId == 0x0BDA -> "RTL-SDR Compatible"
            else -> productName ?: "Unknown SDR Device"
        }

    val frequencyRange: String
        get() = when {
            vendorId == 0x0BDA && productId == 0x2838 -> "24 – 1766 MHz"
            vendorId == 0x0BDA -> "24 – 1700 MHz"
            else -> "Unknown"
        }
}

/**
 * Represents the current state of USB SDR device connection.
 */
sealed class UsbConnectionState {
    /** No SDR device is connected. */
    data object Disconnected : UsbConnectionState()

    /** A device was detected, waiting for user permission. */
    data class PermissionRequested(val device: UsbDeviceInfo) : UsbConnectionState()

    /** Device is connected and ready for use. */
    data class Connected(val device: UsbDeviceInfo) : UsbConnectionState()

    /** Permission was denied by the user. */
    data class PermissionDenied(val device: UsbDeviceInfo) : UsbConnectionState()

    /** An error occurred during connection. */
    data class Error(val message: String, val device: UsbDeviceInfo? = null) : UsbConnectionState()
}
