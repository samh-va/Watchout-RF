package com.watchoutrf.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WatchoutRFColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DeepBlack,
    primaryContainer = Color(0xFF003D22),
    onPrimaryContainer = NeonGreen,

    secondary = CyanBright,
    onSecondary = DeepBlack,
    secondaryContainer = Color(0xFF003D4D),
    onSecondaryContainer = CyanBright,

    tertiary = AmberYellow,
    onTertiary = DeepBlack,

    background = DeepBlack,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = DeepBlack,

    outline = GridLine,
    outlineVariant = Color(0xFF1A1A2A),
)

@Composable
fun WatchoutRFTheme(content: @Composable () -> Unit) {
    val colorScheme = WatchoutRFColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepBlack.toArgb()
            window.navigationBarColor = DeepBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WatchoutRFTypography,
        content = content,
    )
}
