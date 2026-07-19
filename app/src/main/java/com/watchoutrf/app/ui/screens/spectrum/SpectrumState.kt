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
    val activeMarkerX: Float? = null,  // Normalized X position [0..1], null = no active marker
) {
    val allMarkers: List<Marker>
        get() = markers + autoDetectedPeaks

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpectrumState) return false
        
        if (isScanning != other.isScanning) return false
        if (isDemoMode != other.isDemoMode) return false
        if (scanConfig != other.scanConfig) return false
        
        if (currentSpectrum != null) {
            if (other.currentSpectrum == null) return false
            if (!currentSpectrum.contentEquals(other.currentSpectrum)) return false
        } else if (other.currentSpectrum != null) return false
        
        if (maxHoldSpectrum != null) {
            if (other.maxHoldSpectrum == null) return false
            if (!maxHoldSpectrum.contentEquals(other.maxHoldSpectrum)) return false
        } else if (other.maxHoldSpectrum != null) return false
        
        if (markers != other.markers) return false
        if (autoDetectedPeaks != other.autoDetectedPeaks) return false
        if (framesPerSecond != other.framesPerSecond) return false
        if (connectedDeviceName != other.connectedDeviceName) return false
        if (hwError != other.hwError) return false
        if (debugState != other.debugState) return false
        if (activeMarkerX != other.activeMarkerX) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isScanning.hashCode()
        result = 31 * result + isDemoMode.hashCode()
        result = 31 * result + scanConfig.hashCode()
        result = 31 * result + (currentSpectrum?.contentHashCode() ?: 0)
        result = 31 * result + (maxHoldSpectrum?.contentHashCode() ?: 0)
        result = 31 * result + markers.hashCode()
        result = 31 * result + autoDetectedPeaks.hashCode()
        result = 31 * result + framesPerSecond
        result = 31 * result + (connectedDeviceName?.hashCode() ?: 0)
        result = 31 * result + (hwError?.hashCode() ?: 0)
        result = 31 * result + debugState.hashCode()
        result = 31 * result + (activeMarkerX?.hashCode() ?: 0)
        return result
    }
}
