package com.watchoutrf.app.data.dsp

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exponential moving average for spectrum smoothing.
 * Also maintains max-hold trace.
 *
 * Thread-safe: the scan loop feeds it from a background thread while the UI
 * thread resets it (range change, "reset max hold").
 */
@Singleton
class SpectrumAverager @Inject constructor() {

    private var averagedSpectrum: FloatArray? = null
    private var maxHoldSpectrum: FloatArray? = null

    /**
     * Apply exponential averaging to a new spectrum frame.
     * @param newData Raw magnitude values (dBm)
     * @param alpha Smoothing factor (0 = no smoothing, 1 = infinite hold)
     * @return Averaged spectrum
     */
    @Synchronized
    fun average(newData: FloatArray, alpha: Float): FloatArray {
        val current = averagedSpectrum
        if (current == null || current.size != newData.size) {
            averagedSpectrum = newData.copyOf()
            return newData.copyOf()
        }

        for (i in newData.indices) {
            current[i] = current[i] * alpha + newData[i] * (1f - alpha)
        }
        return current.copyOf()
    }

    /**
     * Update max-hold trace.
     * @param newData Raw magnitude values (dBm)
     * @return Max-hold spectrum (maximum value seen at each bin)
     */
    @Synchronized
    fun updateMaxHold(newData: FloatArray): FloatArray {
        val current = maxHoldSpectrum
        if (current == null || current.size != newData.size) {
            maxHoldSpectrum = newData.copyOf()
            return newData.copyOf()
        }

        for (i in newData.indices) {
            if (newData[i] > current[i]) {
                current[i] = newData[i]
            }
        }
        return current.copyOf()
    }

    /**
     * Reset all accumulated data.
     */
    @Synchronized
    fun reset() {
        averagedSpectrum = null
        maxHoldSpectrum = null
    }

    /** Copy of the running average, or null before the first frame. */
    @Synchronized
    fun current(): FloatArray? = averagedSpectrum?.copyOf()

    /** Reset only the max-hold trace, keeping the running average. */
    @Synchronized
    fun resetMaxHold() {
        maxHoldSpectrum = null
    }
}
