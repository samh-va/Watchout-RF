package com.watchoutrf.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watchoutrf.app.ui.theme.*

/**
 * Animated LED-style connection indicator.
 * - Green pulsing = connected
 * - Amber steady = requesting permission
 * - Red steady = disconnected or error
 */
@Composable
fun ConnectionIndicator(
    isConnected: Boolean,
    isRequesting: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val baseColor = when {
        isConnected -> NeonGreen
        isRequesting -> AmberYellow
        else -> ErrorRed
    }

    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "led")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 0.4f else 0.8f,
        targetValue = if (isConnected) 1.0f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnected) 1200 else 1,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = glowAlpha * 0.4f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 2.5f,
            ),
            radius = radius * 2.5f,
            center = center,
        )

        // Main LED
        drawCircle(
            color = baseColor.copy(alpha = glowAlpha),
            radius = radius,
            center = center,
        )

        // Highlight (specular reflection)
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius * 0.35f,
            center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
        )
    }
}
