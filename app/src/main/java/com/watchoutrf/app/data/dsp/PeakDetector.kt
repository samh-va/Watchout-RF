package com.watchoutrf.app.data.dsp

import com.watchoutrf.app.domain.model.Marker
import com.watchoutrf.app.domain.model.SpectrumData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic peak detection in spectrum data.
 * Finds local maxima that are significantly above the noise floor.
 */
@Singleton
class PeakDetector @Inject constructor() {

    /**
     * Detect peaks in the spectrum data.
     * @param data Spectrum data with magnitudes in dBm
     * @param minPeakDbm Minimum amplitude to consider as a peak
     * @param minDistanceBins Minimum distance between peaks in bins
     * @param maxPeaks Maximum number of peaks to return
     * @return List of Markers at detected peak positions
     */
    fun detectPeaks(
        data: SpectrumData,
        minPeakDbm: Float = -80f,
        minDistanceBins: Int = 10,
        maxPeaks: Int = 10,
    ): List<Marker> {
        val magnitudes = data.magnitudes
        if (magnitudes.size < 3) return emptyList()

        // Find all local maxima
        val candidates = mutableListOf<Pair<Int, Float>>() // (bin, magnitude)
        for (i in 1 until magnitudes.size - 1) {
            if (magnitudes[i] > magnitudes[i - 1] &&
                magnitudes[i] > magnitudes[i + 1] &&
                magnitudes[i] >= minPeakDbm
            ) {
                candidates.add(i to magnitudes[i])
            }
        }

        // Sort by magnitude (strongest first)
        candidates.sortByDescending { it.second }

        // Apply minimum distance filter
        val selectedPeaks = mutableListOf<Pair<Int, Float>>()
        for (candidate in candidates) {
            if (selectedPeaks.size >= maxPeaks) break
            val tooClose = selectedPeaks.any { 
                kotlin.math.abs(it.first - candidate.first) < minDistanceBins 
            }
            if (!tooClose) {
                selectedPeaks.add(candidate)
            }
        }

        // Convert to Markers
        return selectedPeaks.mapIndexed { index, (bin, magnitude) ->
            Marker(
                id = index + 1,
                frequencyHz = data.frequencyAtBin(bin),
                amplitudeDbm = magnitude,
            )
        }
    }
}
