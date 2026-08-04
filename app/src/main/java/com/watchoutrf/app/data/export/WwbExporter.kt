package com.watchoutrf.app.data.export

import android.content.Context
import android.net.Uri
import com.watchoutrf.app.domain.model.SpectrumData
import kotlin.math.ceil

/**
 * Exports spectrum scan data to a CSV file compatible with
 * Shure Wireless Workbench 7 (WWB7).
 *
 * Format: no headers, one line per point → "frequency_MHz,level_dBm"
 * e.g.:
 *   470.000,-95.5
 *   470.025,-94.2
 *
 * Minimum step size required by WWB7: 25 kHz.
 * If the spectrum has finer resolution, this exporter automatically
 * decimates by keeping the maximum (peak-hold) value in each 25 kHz window.
 */
object WwbExporter {

    private const val WWB_MIN_STEP_HZ = 25_000.0   // 25 kHz

    /**
     * Writes [spectrumData] to the file represented by [uri] using
     * Android's ContentResolver. Returns null on success, or an error
     * message string on failure.
     */
    fun export(context: Context, spectrumData: SpectrumData, uri: Uri): String? {
        return try {
            val lines = buildCsvLines(spectrumData)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                lines.forEach { writer.appendLine(it) }
            } ?: return "Could not open output stream for the selected file."
            null // success
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }

    /**
     * Builds the CSV lines without writing to disk.
     * Useful for previewing or testing.
     */
    fun buildCsvLines(data: SpectrumData): List<String> {
        val binWidthHz = data.binWidthHz
        val magnitudes = data.magnitudes

        // Decide decimation: if bins are finer than 25 kHz, group them
        val decimFactor = maxOf(1, ceil(WWB_MIN_STEP_HZ / binWidthHz).toInt())

        val lines = mutableListOf<String>()

        var i = 0
        while (i < magnitudes.size) {
            val windowEnd = minOf(i + decimFactor, magnitudes.size)

            // Peak-hold across the decimation window
            var peakDbm = magnitudes[i]
            for (j in i + 1 until windowEnd) {
                if (magnitudes[j] > peakDbm) peakDbm = magnitudes[j]
            }

            // Frequency at center of this window
            val windowCenterBin = i + (windowEnd - i) / 2
            val freqHz = data.frequencyAtBin(windowCenterBin)
            val freqMhz = freqHz / 1_000_000.0

            // WWB7 expects: "470.025,-95.5" (3 decimal places for MHz, 1 for dBm)
            lines.add("%.3f,%.1f".format(freqMhz, peakDbm))

            i += decimFactor
        }

        return lines
    }
}
