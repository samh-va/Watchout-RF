package com.watchoutrf.app.ui.screens.spectrum

import com.watchoutrf.app.domain.model.*

data class SpectrumState(
    val isScanning: Boolean = false,
    /** True when no dongle is connected and we are using synthetic data. */
    val isDemoMode: Boolean = true,
    val scanConfig: ScanConfig = ScanConfig(),
    val currentSpectrum: FloatArray? = null,
    val maxHoldSpectrum: FloatArray? = null,
    val waterfallHistory: List<FloatArray> = emptyList(),
    val markers: List<Marker> = emptyList(),
    val autoDetectedPeaks: List<Marker> = emptyList(),
    val framesPerSecond: Int = 0,
    /** Human-readable name shown in the top bar when hardware is connected. */
    val connectedDeviceName: String? = null,
    /** Non-null when the dongle failed to initialise; shown as an error banner. */
    val hwError: String? = null,
    val debugState: String = "INIT",
) {
    val allMarkers: List<Marker>
        get() = markers + autoDetectedPeaks

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpectrumState) return false
        return isScanning == other.isScanning &&
                isDemoMode == other.isDemoMode &&
                scanConfig == other.scanConfig &&
                currentSpectrum?.contentEquals(other.currentSpectrum ?: FloatArray(0)) != false &&
                markers == other.markers &&
                framesPerSecond == other.framesPerSecond &&
                connectedDeviceName == other.connectedDeviceName &&
                hwError == other.hwError &&
                debugState == other.debugState
    }

    override fun hashCode(): Int = isScanning.hashCode() * 31 + scanConfig.hashCode()
}
