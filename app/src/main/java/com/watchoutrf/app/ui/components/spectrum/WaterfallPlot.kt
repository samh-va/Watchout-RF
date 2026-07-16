package com.watchoutrf.app.ui.components.spectrum

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize

/**
 * Waterfall (spectrogram) display that maps successive spectrum frames to a
 * scrolling colour-mapped image.
 *
 * For performance the implementation builds a single [android.graphics.Bitmap]
 * from the raw dBm data, colours each pixel through the selected colour-map,
 * and draws it scaled to fill the Canvas.
 *
 * @param waterfallHistory List of spectrum frames (newest first). Each [FloatArray]
 *                         contains dBm values across the frequency span.
 * @param referenceLevel   Reference level in dBm (maps to "strong" end of the colour-map).
 * @param dynamicRange     Dynamic range in dB.
 * @param colorMapType     Colour-map selector: 0 = Jet, 1 = Hot, 2 = Viridis.
 * @param modifier         Compose modifier.
 */
@Composable
fun WaterfallPlot(
    waterfallHistory: List<FloatArray>,
    referenceLevel: Float,
    dynamicRange: Float,
    colorMapType: Int = 0,
    modifier: Modifier = Modifier
) {
    // Build the bitmap outside the draw lambda so it is cached per recomposition.
    val imageBitmap = remember(waterfallHistory, referenceLevel, dynamicRange, colorMapType) {
        buildWaterfallBitmap(waterfallHistory, referenceLevel, dynamicRange, colorMapType)
    }

    Canvas(modifier = modifier) {
        if (imageBitmap != null) {
            drawImage(
                image = imageBitmap,
                srcOffset = Offset.Zero.let { IntSize(0, 0) }.let { androidx.compose.ui.unit.IntOffset(0, 0) },
                srcSize = IntSize(imageBitmap.width, imageBitmap.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bitmap builder
// ---------------------------------------------------------------------------

/**
 * Converts the raw waterfall history into an [androidx.compose.ui.graphics.ImageBitmap].
 * Returns `null` when the history is empty.
 */
private fun buildWaterfallBitmap(
    history: List<FloatArray>,
    referenceLevel: Float,
    dynamicRange: Float,
    colorMapType: Int
): androidx.compose.ui.graphics.ImageBitmap? {
    if (history.isEmpty()) return null

    val numRows = history.size
    val numBins = history.maxOf { it.size }
    if (numBins == 0) return null

    val pixels = IntArray(numBins * numRows)

    for (row in 0 until numRows) {
        val frame = history[row]
        val rowOffset = row * numBins
        for (col in 0 until numBins) {
            val dbm = if (col < frame.size) frame[col] else referenceLevel - dynamicRange
            // normalized: 0 = strong signal (at referenceLevel), 1 = weak (at noise floor)
            val normalized = ((referenceLevel - dbm) / dynamicRange).coerceIn(0f, 1f)
            pixels[rowOffset + col] = mapToColor(normalized, colorMapType)
        }
    }

    val bitmap = Bitmap.createBitmap(numBins, numRows, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, numBins, 0, 0, numBins, numRows)
    return bitmap.asImageBitmap()
}

// ---------------------------------------------------------------------------
// Colour-map helpers
// ---------------------------------------------------------------------------

/**
 * Maps a normalised amplitude value to an ARGB [Int] colour through the
 * selected colour-map.
 *
 * @param n           Normalised value in [0, 1] where **0 = strong** and **1 = weak**.
 * @param colorMapType 0 = Jet, 1 = Hot, 2 = Viridis.
 */
private fun mapToColor(n: Float, colorMapType: Int): Int {
    // Invert so t = 0 → weak, t = 1 → strong (makes map functions more intuitive)
    val t = 1f - n
    return when (colorMapType) {
        0 -> jetColorMap(t)
        1 -> hotColorMap(t)
        2 -> viridisColorMap(t)
        else -> jetColorMap(t)
    }
}

/**
 * Jet colour-map: blue → cyan → green → yellow → red.
 * @param t 0 = weak (blue), 1 = strong (red).
 */
private fun jetColorMap(t: Float): Int {
    val r: Float
    val g: Float
    val b: Float
    when {
        t < 0.125f -> {
            r = 0f; g = 0f; b = 0.5f + t / 0.125f * 0.5f
        }
        t < 0.375f -> {
            r = 0f; g = (t - 0.125f) / 0.25f; b = 1f
        }
        t < 0.625f -> {
            r = (t - 0.375f) / 0.25f; g = 1f; b = 1f - (t - 0.375f) / 0.25f
        }
        t < 0.875f -> {
            r = 1f; g = 1f - (t - 0.625f) / 0.25f; b = 0f
        }
        else -> {
            r = 1f - (t - 0.875f) / 0.125f * 0.5f; g = 0f; b = 0f
        }
    }
    return packArgb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
}

/**
 * Hot colour-map: black → red → yellow → white.
 * @param t 0 = weak (black), 1 = strong (white).
 */
private fun hotColorMap(t: Float): Int {
    val r: Float
    val g: Float
    val b: Float
    when {
        t < 0.333f -> {
            r = t / 0.333f; g = 0f; b = 0f
        }
        t < 0.666f -> {
            r = 1f; g = (t - 0.333f) / 0.333f; b = 0f
        }
        else -> {
            r = 1f; g = 1f; b = (t - 0.666f) / 0.334f
        }
    }
    return packArgb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
}

/**
 * Viridis-inspired colour-map: dark purple → teal → green → yellow.
 * Uses a simplified piecewise approximation of the Viridis palette.
 * @param t 0 = weak (dark purple), 1 = strong (yellow).
 */
private fun viridisColorMap(t: Float): Int {
    val r: Float
    val g: Float
    val b: Float
    when {
        t < 0.25f -> {
            val s = t / 0.25f
            r = 0.267f + s * (0.282f - 0.267f)
            g = 0.004f + s * (0.141f - 0.004f)
            b = 0.329f + s * (0.458f - 0.329f)
        }
        t < 0.5f -> {
            val s = (t - 0.25f) / 0.25f
            r = 0.282f - s * (0.282f - 0.127f)
            g = 0.141f + s * (0.567f - 0.141f)
            b = 0.458f + s * (0.551f - 0.458f)
        }
        t < 0.75f -> {
            val s = (t - 0.5f) / 0.25f
            r = 0.127f + s * (0.741f - 0.127f)
            g = 0.567f + s * (0.873f - 0.567f)
            b = 0.551f - s * (0.551f - 0.150f)
        }
        else -> {
            val s = (t - 0.75f) / 0.25f
            r = 0.741f + s * (0.993f - 0.741f)
            g = 0.873f + s * (0.906f - 0.873f)
            b = 0.150f + s * (0.144f - 0.150f)
        }
    }
    return packArgb(
        255,
        (r * 255).toInt().coerceIn(0, 255),
        (g * 255).toInt().coerceIn(0, 255),
        (b * 255).toInt().coerceIn(0, 255)
    )
}

/** Pack ARGB components into a single [Int] in Android's native order. */
private fun packArgb(a: Int, r: Int, g: Int, b: Int): Int {
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
