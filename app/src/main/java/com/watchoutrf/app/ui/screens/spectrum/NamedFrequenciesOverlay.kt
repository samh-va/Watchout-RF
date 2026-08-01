package com.watchoutrf.app.ui.screens.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

@Composable
fun NamedFrequenciesOverlay(
    frequencies: List<com.watchoutrf.app.domain.model.NamedFrequency>,
    startHz: Long,
    endHz: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spanHz = endHz - startHz
        
        if (spanHz <= 0) return@Canvas

        for (freq in frequencies) {
            if (!freq.isVisible) continue
            
            // Check if freq is in range
            if (freq.frequencyHz < startHz || freq.frequencyHz > endHz) continue
            
            val normalizedX = (freq.frequencyHz - startHz).toFloat() / spanHz.toFloat()
            val x = normalizedX * w
            
            val color = try { Color(android.graphics.Color.parseColor(freq.colorHex)) } catch (e: Exception) { Color(0xFFFF6B00) }
            
            // Vertical line
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
            
            // Draw a small diamond at the top
            val diamondSize = 4.dp.toPx()
            val diamondPath = Path().apply {
                moveTo(x, 0f)
                lineTo(x + diamondSize, diamondSize)
                lineTo(x, diamondSize * 2)
                lineTo(x - diamondSize, diamondSize)
                close()
            }
            drawPath(diamondPath, color = color)
        }
    }
}
