package com.watchoutrf.app.domain.model

/**
 * Configuration parameters for a spectrum scan.
 */
data class ScanConfig(
    val frequencyRange: FrequencyRange = FrequencyRange.IEM_RANGE,
    val numBins: Int = 1024,
    val gainMode: GainMode = GainMode.Auto,
    val manualGainDb: Int = 40,
    val windowType: WindowType = WindowType.HAMMING,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val averagingFactor: Float = 0.5f,     // 0 = no averaging, 1 = full hold
    val maxHoldEnabled: Boolean = true,
    val referenceLevel: Float = -20f,      // dBm, top of display
    val dynamicRange: Float = 80f,         // dB, from reference to bottom
)

enum class ScanMode(val displayName: String) {
    CONTINUOUS("Continuous"),
    SINGLE("Single Sweep"),
    MAX_HOLD("Max Hold"),
}
