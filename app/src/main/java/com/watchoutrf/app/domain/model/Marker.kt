package com.watchoutrf.app.domain.model

/**
 * A marker placed on the spectrum display.
 */
data class Marker(
    val id: Int,
    val frequencyHz: Double,
    val amplitudeDbm: Float,
    val label: String = "M${id}",
    val color: MarkerColor = MarkerColor.CYAN,
) {
    fun formatFrequency(): String {
        return when {
            frequencyHz >= 1e9 -> String.format("%.3f GHz", frequencyHz / 1e9)
            frequencyHz >= 1e6 -> String.format("%.3f MHz", frequencyHz / 1e6)
            frequencyHz >= 1e3 -> String.format("%.1f kHz", frequencyHz / 1e3)
            else -> String.format("%.0f Hz", frequencyHz)
        }
    }

    fun formatAmplitude(): String = String.format("%.1f dBm", amplitudeDbm)
}

enum class MarkerColor {
    CYAN, YELLOW, MAGENTA, GREEN, RED, BLUE, ORANGE, WHITE
}
