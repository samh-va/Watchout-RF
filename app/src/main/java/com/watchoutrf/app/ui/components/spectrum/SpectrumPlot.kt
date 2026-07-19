package com.watchoutrf.app.ui.components.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.watchoutrf.app.ui.theme.AmberYellow
import com.watchoutrf.app.ui.theme.CyanBright
import com.watchoutrf.app.ui.theme.GridLine
import com.watchoutrf.app.ui.theme.TextDim

/**
 * A Compose Canvas that renders the primary spectrum analyzer plot.
 *
 * Draws the spectrum trace as a smooth path in CyanBright with a semi-transparent
 * gradient fill beneath, horizontal/vertical grid lines every 10 dB / 8 divisions,
 * an optional max-hold trace in AmberYellow, and a subtle noise-floor reference line.
 *
 * @param spectrumData   Array of dBm amplitude values across the frequency span, or null when idle.
 * @param maxHoldData    Optional max-hold envelope (same length as spectrumData).
 * @param referenceLevel Top-of-display reference level in dBm (e.g. -20).
 * @param dynamicRange   Vertical span in dB (e.g. 80 → display covers referenceLevel..(referenceLevel-dynamicRange)).
 * @param modifier       Compose modifier for sizing and layout.
 */
@Composable
fun SpectrumPlot(
    spectrumData: FloatArray?,
    maxHoldData: FloatArray?,
    referenceLevel: Float,
    dynamicRange: Float,
    modifier: Modifier = Modifier
) {
    val traceColor = CyanBright
    val fillColor = CyanBright.copy(alpha = 0.15f)
    val maxHoldColor = AmberYellow.copy(alpha = 0.6f)
    val gridColor = GridLine
    val noiseFloorColor = TextDim.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // --- Horizontal grid lines (every 10 dB) ---
        drawHorizontalGrid(w, h, referenceLevel, dynamicRange, gridColor)

        // --- Vertical grid lines (8 evenly-spaced divisions) ---
        drawVerticalGrid(w, h, gridColor)

        // --- Noise floor reference ---
        drawLine(
            color = noiseFloorColor,
            start = Offset(0f, h - 1.dp.toPx()),
            end = Offset(w, h - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )

        // --- Max-hold trace ---
        if (maxHoldData != null && maxHoldData.isNotEmpty()) {
            val maxHoldPath = buildTracePath(maxHoldData, w, h, referenceLevel, dynamicRange)
            drawPath(
                path = maxHoldPath,
                color = maxHoldColor,
                style = Stroke(
                    width = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // --- Main spectrum trace + fill ---
        if (spectrumData != null && spectrumData.isNotEmpty()) {
            val tracePath = buildTracePath(spectrumData, w, h, referenceLevel, dynamicRange)

            // Semi-transparent fill under the curve
            val fillPath = Path().apply {
                addPath(tracePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path = fillPath, color = fillColor, style = Fill)

            // Solid trace on top
            drawPath(
                path = tracePath,
                color = traceColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Maps a dBm value to a vertical pixel coordinate, clamped to [0, height].
 */
private fun dbmToY(dbm: Float, height: Float, referenceLevel: Float, dynamicRange: Float): Float {
    return (height * (referenceLevel - dbm) / dynamicRange).coerceIn(0f, height)
}

private fun DrawScope.buildTracePath(
    data: FloatArray,
    w: Float,
    h: Float,
    referenceLevel: Float,
    dynamicRange: Float
): Path {
    val path = Path()
    val count = data.size
    if (count == 0) return path

    // Hardware canvas drops paths with too many points.
    // Decimate to approx screen width (e.g. 2000 points max) using peak-picking.
    val maxPoints = w.toInt().coerceIn(500, 4000)


    if (count <= maxPoints) {
        val xStep = if (count > 1) w / (count - 1).toFloat() else 0f
        path.moveTo(0f, dbmToY(data[0], h, referenceLevel, dynamicRange))
        for (i in 1 until count) {
            path.lineTo(i * xStep, dbmToY(data[i], h, referenceLevel, dynamicRange))
        }
        return path
    }

    // Decimation: for each pixel column, find the MAX value in that chunk
    val pointsToDraw = maxPoints
    val chunkStep = count.toFloat() / pointsToDraw
    val xStep = w / (pointsToDraw - 1).toFloat()

    var chunkStart = 0f
    path.moveTo(0f, dbmToY(data[0], h, referenceLevel, dynamicRange))

    for (i in 0 until pointsToDraw) {
        val startIdx = chunkStart.toInt().coerceIn(0, count - 1)
        chunkStart += chunkStep
        val endIdx = chunkStart.toInt().coerceIn(0, count)
        
        var maxVal = -300f
        for (j in startIdx until endIdx) {
            if (data[j] > maxVal) maxVal = data[j]
        }
        if (maxVal == -300f) maxVal = data[startIdx]
        
        val x = i * xStep
        val y = dbmToY(maxVal, h, referenceLevel, dynamicRange)
        path.lineTo(x, y)
    }

    return path
}

/**
 * Draws horizontal grid lines every 10 dB within the visible dynamic range.
 */
private fun DrawScope.drawHorizontalGrid(
    w: Float,
    h: Float,
    referenceLevel: Float,
    dynamicRange: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val stepDb = 10f
    val bottomLevel = referenceLevel - dynamicRange
    var level = referenceLevel
    while (level >= bottomLevel) {
        val y = dbmToY(level, h, referenceLevel, dynamicRange)
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.5f.dp.toPx()
        )
        level -= stepDb
    }
}

/**
 * Draws 8 evenly-spaced vertical grid lines.
 */
private fun DrawScope.drawVerticalGrid(
    w: Float,
    h: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val divisions = 8
    for (i in 1 until divisions) {
        val x = w * i / divisions
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.5f.dp.toPx()
        )
    }
}
