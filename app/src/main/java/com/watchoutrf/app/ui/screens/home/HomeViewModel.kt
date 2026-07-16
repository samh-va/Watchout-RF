package com.watchoutrf.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchoutrf.app.data.usb.UsbConnectionState
import com.watchoutrf.app.data.usb.UsbDeviceInfo
import com.watchoutrf.app.data.usb.UsbDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI State for the Home screen.
 */
data class HomeUiState(
    val isDeviceConnected: Boolean = false,
    val isRequestingPermission: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val deviceInfo: UsbDeviceInfo? = null,
    val errorMessage: String? = null,
    val statusText: String = "No device",
    val nativeVersion: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usbDeviceManager: UsbDeviceManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = usbDeviceManager.connectionState
        .map { connectionState ->
            when (connectionState) {
                is UsbConnectionState.Disconnected -> HomeUiState(
                    statusText = "No device",
                )
                is UsbConnectionState.PermissionRequested -> HomeUiState(
                    isRequestingPermission = true,
                    deviceInfo = connectionState.device,
                    statusText = "Requesting permission...",
                )
                is UsbConnectionState.Connected -> HomeUiState(
                    isDeviceConnected = true,
                    deviceInfo = connectionState.device,
                    statusText = connectionState.device.displayName,
                )
                is UsbConnectionState.PermissionDenied -> HomeUiState(
                    isPermissionDenied = true,
                    deviceInfo = connectionState.device,
                    statusText = "Permission denied",
                )
                is UsbConnectionState.Error -> HomeUiState(
                    errorMessage = connectionState.message,
                    deviceInfo = connectionState.device,
                    statusText = "Error",
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(),
        )

    fun startUsbMonitoring() {
        usbDeviceManager.startMonitoring()
    }

    fun stopUsbMonitoring() {
        usbDeviceManager.stopMonitoring()
    }

    fun retryConnection() {
        usbDeviceManager.scanForDevices()
    }

    /**
     * Load native library version to verify JNI bridge.
     */
    fun getNativeVersion(): String {
        return try {
            val nativeLib = com.watchoutrf.app.NativeLib()
            nativeLib.getVersion()
        } catch (e: UnsatisfiedLinkError) {
            "Native lib not loaded"
        }
    }
}
