package com.watchoutrf.app.domain.model

/**
 * A single frame of spectrum data (one sweep).
 */
data class SpectrumData(
    val frequencyStartHz: Long,
    val frequencyEndHz: Long,
    val magnitudes: FloatArray,   // dBm values per bin
    val timestamp: Long = System.currentTimeMillis(),
) {
    val numBins: Int get() = magnitudes.size
    val binWidthHz: Double get() = (frequencyEndHz - frequencyStartHz).toDouble() / numBins

    fun frequencyAtBin(bin: Int): Double {
        return frequencyStartHz + bin * binWidthHz
    }

    fun binAtFrequency(freqHz: Double): Int {
        return ((freqHz - frequencyStartHz) / binWidthHz).toInt().coerceIn(0, numBins - 1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpectrumData) return false
        return frequencyStartHz == other.frequencyStartHz &&
                frequencyEndHz == other.frequencyEndHz &&
                magnitudes.contentEquals(other.magnitudes)
    }

    override fun hashCode(): Int {
        var result = frequencyStartHz.hashCode()
        result = 31 * result + frequencyEndHz.hashCode()
        result = 31 * result + magnitudes.contentHashCode()
        return result
    }
}
