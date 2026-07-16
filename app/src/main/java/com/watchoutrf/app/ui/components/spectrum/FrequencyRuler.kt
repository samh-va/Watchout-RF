package com.watchoutrf.app.ui.components.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchoutrf.app.ui.theme.GridLine
import com.watchoutrf.app.ui.theme.TextSecondary

/**
 * Frequency axis ruler drawn below the spectrum plot.
 *
 * Automatically selects a human-friendly tick interval (1 / 2 / 5 MHz
 * multiples) based on the displayed span, renders major and minor tick marks,
 * and labels major ticks in MHz.
 *
 * @param startHz      Lower bound of the displayed frequency range in Hz.
 * @param endHz        Upper bound of the displayed frequency range in Hz.
 * @param textMeasurer A [TextMeasurer] obtained via `rememberTextMeasurer()` by the caller.
 * @param modifier     Compose modifier – recommended height ≈ 32.dp.
 */
@Composable
fun FrequencyRuler(
    startHz: Long,
    endHz: Long,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {
    val tickColor = GridLine
    val labelColor = TextSecondary
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spanHz = (endHz - startHz).toFloat()
        if (spanHz <= 0f) return@Canvas

        val majorTickHeight = 8.dp.toPx()
        val minorTickHeight = 4.dp.toPx()
        val tickWidth = 0.5f.dp.toPx()

        // ---- Determine tick interval ----
        val majorIntervalHz = chooseMajorInterval(spanHz)
        val minorIntervalHz = majorIntervalHz / 5.0 // 5 minor ticks per major

        // ---- Draw minor ticks ----
        val firstMinor = (Math.ceil(startHz / minorIntervalHz) * minorIntervalHz).toLong()
        var freq = firstMinor
        while (freq <= endHz) {
            val x = w * (freq - startHz).toFloat() / spanHz
            drawLine(
                color = tickColor,
                start = Offset(x, 0f),
                end = Offset(x, minorTickHeight),
                strokeWidth = tickWidth
            )
            freq += minorIntervalHz.toLong()
        }

        // ---- Draw major ticks + labels ----
        val firstMajor = (Math.ceil(startHz.toDouble() / majorIntervalHz) * majorIntervalHz).toLong()
        freq = firstMajor
        while (freq <= endHz) {
            val x = w * (freq - startHz).toFloat() / spanHz

            // Major tick line
            drawLine(
                color = tickColor,
                start = Offset(x, 0f),
                end = Offset(x, majorTickHeight),
                strokeWidth = tickWidth
            )

            // Label (MHz)
            val labelMHz = freq / 1_000_000.0
            val labelText = if (labelMHz == labelMHz.toLong().toDouble()) {
                labelMHz.toLong().toString()
            } else {
                String.format("%.1f", labelMHz)
            }

            val measuredText = textMeasurer.measure(labelText, labelStyle)
            val labelX = x - measuredText.size.width / 2f
            val labelY = majorTickHeight + 2.dp.toPx()

            // Only draw if the label fits within the canvas
            if (labelX >= -measuredText.size.width / 2f && labelX + measuredText.size.width <= w + measuredText.size.width / 2f) {
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(labelX, labelY)
                )
            }

            freq += majorIntervalHz.toLong()
        }
    }
}

// ---------------------------------------------------------------------------
// Tick-interval selection
// ---------------------------------------------------------------------------

/**
 * Selects a "nice" major-tick interval for the given frequency span.
 *
 * Produces intervals from the 1-2-5 sequence scaled by powers of ten,
 * targeting roughly 6–12 major ticks across the display.
 */
private fun chooseMajorInterval(spanHz: Float): Double {
    val idealCount = 8.0
    val rawInterval = spanHz / idealCount

    // Find the order of magnitude
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawInterval)))
    val normalized = rawInterval / magnitude

    val niceMultiplier = when {
        normalized < 1.5  -> 1.0
        normalized < 3.5  -> 2.0
        normalized < 7.5  -> 5.0
        else              -> 10.0
    }

    return niceMultiplier * magnitude
}
