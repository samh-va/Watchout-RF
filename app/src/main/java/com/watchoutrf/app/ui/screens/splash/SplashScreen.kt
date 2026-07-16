package com.watchoutrf.app.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchoutrf.app.ui.theme.CyanBright
import com.watchoutrf.app.ui.theme.DeepBlack
import com.watchoutrf.app.ui.theme.NeonGreen
import com.watchoutrf.app.ui.theme.TextDim
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {

    // Auto-navigate after 2500ms
    LaunchedEffect(Unit) {
        delay(2500L)
        onSplashFinished()
    }

    // Text fade-in state
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500L)
        textVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // --- Three concentric radio wave rings ---
    val ring1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val ring2Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                delayMillis = 666,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2",
    )
    val ring3Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                delayMillis = 1333,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring3",
    )

    // --- Center dot pulse ---
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // Text alpha animation
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, delayMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "textFade",
    )
    // Use a simpler approach: just use textVisible for the alpha
    val finalTextAlpha = if (textVisible) 1f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center,
    ) {
        // --- Animated rings and center dot ---
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.4f

            // Draw ring helper
            fun drawRing(progress: Float) {
                val radius = maxRadius * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.7f
                drawCircle(
                    color = CyanBright.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            drawRing(ring1Progress)
            drawRing(ring2Progress)
            drawRing(ring3Progress)

            // Pulsing radial gradient glow
            val glowRadius = 48.dp.toPx() * glowPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonGreen.copy(alpha = 0.6f * glowPulse),
                        NeonGreen.copy(alpha = 0.2f * glowPulse),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )

            // Solid center dot
            drawCircle(
                color = NeonGreen,
                radius = 6.dp.toPx(),
                center = center,
            )
        }

        // --- Text block below center ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 80.dp)
                .alpha(finalTextAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "WatchoutRF",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SPECTRUM ANALYZER",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                ),
                color = CyanBright,
            )
        }

        // --- Version text at bottom ---
        Text(
            text = "v0.1.0",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-32).dp),
        )
    }
}
