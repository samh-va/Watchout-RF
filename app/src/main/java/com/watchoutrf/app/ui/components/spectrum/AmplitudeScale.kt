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
import com.watchoutrf.app.ui.theme.TextDim
import com.watchoutrf.app.ui.theme.TextSecondary

/**
 * Amplitude axis scale drawn to the left of the spectrum plot.
 *
 * Renders right-aligned dBm labels at every 10 dB step, a small tick mark at
 * each label, and a "dBm" unit indicator at the top.
 *
 * @param referenceLevel Top-of-display reference level in dBm.
 * @param dynamicRange   Displayed dynamic range in dB.
 * @param textMeasurer   A [TextMeasurer] obtained via `rememberTextMeasurer()` by the caller.
 * @param modifier       Compose modifier – recommended width ≈ 48.dp.
 */
@Composable
fun AmplitudeScale(
    referenceLevel: Float,
    dynamicRange: Float,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {
    val labelColor = TextSecondary
    val unitColor = TextDim
    val tickColor = GridLine

    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val unitStyle = TextStyle(color = unitColor, fontSize = 9.sp)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val tickLength = 4.dp.toPx()
        val tickWidth = 0.5f.dp.toPx()
        val labelPaddingEnd = 4.dp.toPx() // gap between label and tick
        val stepDb = 10f

        // ---- "dBm" unit label at the top ----
        val unitText = textMeasurer.measure("dBm", unitStyle)
        drawText(
            textLayoutResult = unitText,
            topLeft = Offset(w - tickLength - labelPaddingEnd - unitText.size.width, 2.dp.toPx())
        )

        // ---- Labels every 10 dB ----
        val bottomLevel = referenceLevel - dynamicRange
        var level = referenceLevel
        while (level >= bottomLevel) {
            val y = h * (referenceLevel - level) / dynamicRange

            // Tick mark (extends rightward from the label column)
            drawLine(
                color = tickColor,
                start = Offset(w - tickLength, y),
                end = Offset(w, y),
                strokeWidth = tickWidth
            )

            // Right-aligned label
            val labelText = level.toInt().toString()
            val measured = textMeasurer.measure(labelText, labelStyle)
            val labelX = w - tickLength - labelPaddingEnd - measured.size.width
            val labelY = y - measured.size.height / 2f

            // Clamp so labels at the very top/bottom don't clip
            val clampedY = labelY.coerceIn(0f, h - measured.size.height.toFloat())
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(labelX, clampedY)
            )

            level -= stepDb
        }
    }
}
