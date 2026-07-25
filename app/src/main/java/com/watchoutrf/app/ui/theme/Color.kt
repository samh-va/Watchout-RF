package com.watchoutrf.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// WatchoutRF Color Palette
// Professional RF instrument aesthetic (dark theme)
// ============================================================

// Background tones
val DeepBlack = Color(0xFF0A0A0F)
val DarkSurface = Color(0xFF141420)
val DarkSurfaceVariant = Color(0xFF1E1E2E)
val GridLine = Color(0xFF2A2A3E)

// Primary accent — Neon Green (buttons, active states)
val NeonGreen = Color(0xFF00FF88)
val NeonGreenDim = Color(0xFF00CC6A)

// Secondary accent — Cyan (spectrum trace)
val CyanBright = Color(0xFF00E5FF)
val CyanDim = Color(0xFF0097A7)

// Tertiary — Amber (max hold, warnings)
val AmberYellow = Color(0xFFFFD600)

// Semantic colors
val WarningOrange = Color(0xFFFF6B35)
val ErrorRed = Color(0xFFFF4757)
val SuccessGreen = Color(0xFF2ED573)

// Text hierarchy
val TextPrimary = Color(0xFFE0E0EC)
val TextSecondary = Color(0xFF7A7A8E)
val TextDim = Color(0xFF4A4A5E)

// Spectrum-specific colors
val SpectrumTrace = CyanBright
val SpectrumMaxHold = AmberYellow
val SpectrumFill = Color(0x3300E5FF) // 20% opacity cyan

// Waterfall gradient stops
val WaterfallLow = Color(0xFF000033)
val WaterfallMidLow = Color(0xFF0066CC)
val WaterfallMid = Color(0xFF00CCCC)
val WaterfallMidHigh = Color(0xFFFFCC00)
val WaterfallHigh = Color(0xFFFF3300)

// Brand aliases (for FrequencyManagerSheet and legacy references)
val BrandOrange = WarningOrange
val DarkBackground = DeepBlack
val BrandRed = ErrorRed
