package com.watchoutrf.app.data.dsp

import com.watchoutrf.app.domain.model.SpectrumData
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generates realistic-looking synthetic RF spectrum data for demo/testing mode.
 * Produces a noise floor with Gaussian variation and overlays predefined signal
 * sources as Gaussian-shaped peaks using proper power addition in linear scale.
 */
@Singleton
class SyntheticSignalGenerator @Inject constructor() {

    private val random = Random()

    private data class SignalSource(
        val centerFreqHz: Double,
        val peakDbm: Double,
        val bandwidthHz: Double,
        val name: String,
    )

    private val signals = listOf(
        // FM broadcast stations
        SignalSource(88_100_000.0, -35.0, 200_000.0, "FM 88.1"),
        SignalSource(92_500_000.0, -28.0, 200_000.0, "FM 92.5"),
        SignalSource(96_300_000.0, -42.0, 200_000.0, "FM 96.3"),
        SignalSource(101_100_000.0, -31.0, 200_000.0, "FM 101.1"),
        SignalSource(105_700_000.0, -38.0, 200_000.0, "FM 105.7"),

        // UHF TV channels (6 MHz bandwidth)
        SignalSource(490_000_000.0, -45.0, 6_000_000.0, "UHF Ch 16"),
        SignalSource(530_000_000.0, -52.0, 6_000_000.0, "UHF Ch 23"),
        SignalSource(578_000_000.0, -48.0, 6_000_000.0, "UHF Ch 31"),
        SignalSource(626_000_000.0, -55.0, 6_000_000.0, "UHF Ch 39"),
        SignalSource(674_000_000.0, -50.0, 6_000_000.0, "UHF Ch 47"),

        // Wireless microphones (200 kHz bandwidth)
        SignalSource(542_500_000.0, -62.0, 200_000.0, "Wireless Mic 1"),
        SignalSource(566_000_000.0, -58.0, 200_000.0, "Wireless Mic 2"),
        SignalSource(590_500_000.0, -65.0, 200_000.0, "Wireless Mic 3"),

        // In-ear monitors (200 kHz bandwidth)
        SignalSource(554_000_000.0, -55.0, 200_000.0, "IEM 1"),
        SignalSource(608_000_000.0, -60.0, 200_000.0, "IEM 2"),
    )

    /**
     * Generate a single frame of synthetic spectrum data.
     *
     * @param startFreqHz Start frequency of the sweep in Hz
     * @param endFreqHz End frequency of the sweep in Hz
     * @param numBins Number of frequency bins to generate
     * @param noiseFloorDbm Base noise floor level in dBm
     * @return SpectrumData containing the generated magnitudes
     */
    fun generateSpectrum(
        startFreqHz: Long,
        endFreqHz: Long,
        numBins: Int,
        noiseFloorDbm: Double = -95.0,
    ): SpectrumData {
        val magnitudes = FloatArray(numBins)
        val binWidthHz = (endFreqHz - startFreqHz).toDouble() / numBins
        val timeNow = System.nanoTime()

        for (i in 0 until numBins) {
            val freqHz = startFreqHz + i * binWidthHz

            // Noise floor with Gaussian variation (σ = 3 dB)
            val noise = noiseFloorDbm + random.nextGaussian() * 3.0

            // Start with noise in linear power scale
            var linearPower = 10.0.pow(noise / 10.0)

            // Add contributions from each signal source in range
            for (signal in signals) {
                val distanceHz = freqHz - signal.centerFreqHz
                val sigma = signal.bandwidthHz / 2.0

                // Only compute if within ~4 sigma (significant contribution range)
                if (kotlin.math.abs(distanceHz) <= sigma * 4.0) {
                    val gaussianShape = exp(-(distanceHz * distanceHz) / (2.0 * sigma * sigma))
                    val signalDbm = signal.peakDbm * gaussianShape +
                            noiseFloorDbm * (1.0 - gaussianShape)
                    val signalLinear = 10.0.pow(signalDbm / 10.0)

                    // Power addition in linear scale
                    linearPower += signalLinear
                }
            }

            // Convert back to dBm
            var magnitudeDbm = 10.0 * log10(linearPower)

            // Add time-varying component for animation
            magnitudeDbm += sin(timeNow / 1e9 * 0.5 + i * 0.01) * 1.5

            magnitudes[i] = magnitudeDbm.toFloat()
        }

        return SpectrumData(
            frequencyStartHz = startFreqHz,
            frequencyEndHz = endFreqHz,
            magnitudes = magnitudes,
        )
    }
}
