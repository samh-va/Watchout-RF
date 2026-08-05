package com.watchoutrf.app.ui.screens.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size

@Composable
fun NamedFrequenciesOverlay(
    frequencies: List<com.watchoutrf.app.domain.model.NamedFrequency>,
    startHz: Long,
    endHz: Long,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spanHz = endHz - startHz
        
        if (spanHz <= 0) return@Canvas

        // Sort visible frequencies so staggering is consistent across X axis
        val visibleFrequencies = frequencies
            .filter { it.isVisible && it.frequencyHz in startHz..endHz }
            .sortedBy { it.frequencyHz }

        for ((index, freq) in visibleFrequencies.withIndex()) {
            val normalizedX = (freq.frequencyHz - startHz).toFloat() / spanHz.toFloat()
            val x = normalizedX * w
            
            val color = try { Color(android.graphics.Color.parseColor(freq.colorHex)) } catch (e: Exception) { Color(0xFFFF6B00) }
            
            // Solid vertical line (removed dash effect)
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw a small diamond at the bottom instead of top to leave room for labels
            val diamondSize = 4.dp.toPx()
            val diamondPath = Path().apply {
                moveTo(x, h)
                lineTo(x + diamondSize, h - diamondSize)
                lineTo(x, h - diamondSize * 2)
                lineTo(x - diamondSize, h - diamondSize)
                close()
            }
            drawPath(diamondPath, color = color)

            // Draw Label Staggered at the Top
            val labelText = freq.name
            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            )

            val labelWidth = textLayoutResult.size.width.toFloat()
            val labelHeight = textLayoutResult.size.height.toFloat()
            val padding = 4.dp.toPx()
            
            // Stagger Y position to avoid overlap (4 levels)
            val level = index % 4
            val yOffset = 8.dp.toPx() + (level * (labelHeight + padding * 3))

            // Keep label within bounds horizontally
            val labelX = (x - (labelWidth / 2f)).coerceIn(0f, w - labelWidth - padding * 2)
            val labelY = yOffset

            // Background rectangle
            drawRoundRect(
                color = Color(0xCC000000),
                topLeft = Offset(labelX, labelY),
                size = Size(labelWidth + padding * 2, labelHeight + padding * 2),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            // Border
            drawRoundRect(
                color = color,
                topLeft = Offset(labelX, labelY),
                size = Size(labelWidth + padding * 2, labelHeight + padding * 2),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            
            // Text
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(labelX + padding, labelY + padding),
            )
        }
    }
}
