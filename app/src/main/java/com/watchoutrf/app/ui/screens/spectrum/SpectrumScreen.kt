package com.watchoutrf.app.ui.screens.spectrum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchoutrf.app.ui.components.controls.FrequencyRangeSelector
import com.watchoutrf.app.ui.components.controls.ScanModeSelector
import com.watchoutrf.app.ui.components.spectrum.*
import com.watchoutrf.app.ui.theme.*

@Composable
fun SpectrumScreen(
    viewModel: SpectrumViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    // Auto-start scanning on composition
    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
    ) {
        // ═══════════════════════════════════════════════
        // TOP STATUS BAR
        // ═══════════════════════════════════════════════
        TopStatusBar(
            state = state,
            onNavigateBack = onNavigateBack,
        )

        // ═══════════════════════════════════════════════
        // MAIN CONTENT: Control Panel + Spectrum Area
        // ═══════════════════════════════════════════════
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // ─── LEFT CONTROL PANEL ───
            ControlPanel(
                state = state,
                onRangeSelected = viewModel::updateFrequencyRange,
                onModeChanged = viewModel::updateScanMode,
                onToggleMaxHold = viewModel::toggleMaxHold,
                onStartStop = {
                    if (state.isScanning) viewModel.stopScanning()
                    else viewModel.startScanning()
                },
                onResetMaxHold = viewModel::resetMaxHold,
                onClearMarkers = viewModel::clearMarkers,
            )

            // ─── RIGHT SPECTRUM AREA ───
            SpectrumArea(
                state = state,
                textMeasurer = textMeasurer,
                onTapSpectrum = { normalizedX ->
                    viewModel.addMarker(normalizedX)
                },
            )
        }

        // ═══════════════════════════════════════════════
        // BOTTOM MARKER BAR
        // ═══════════════════════════════════════════════
        BottomMarkerBar(state = state)
    }
}

// ════════════════════════════════════════════════════════
// TOP STATUS BAR
// ════════════════════════════════════════════════════════
@Composable
private fun TopStatusBar(
    state: SpectrumState,
    onNavigateBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        // App title
        Text(
            text = "WatchoutRF",
            style = MaterialTheme.typography.labelLarge,
            color = NeonGreen,
            modifier = Modifier.padding(start = 4.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        if (state.hwError != null) {
            Text(
                text = state.hwError,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                ),
                color = DeepBlack,
                modifier = Modifier
                    .background(ErrorRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        // Device status badge
        if (state.isDemoMode) {
            Text(
                text = "DEMO MODE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                ),
                color = DeepBlack,
                modifier = Modifier
                    .background(AmberYellow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else if (state.connectedDeviceName != null) {
            Text(
                text = state.connectedDeviceName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                ),
                color = DeepBlack,
                modifier = Modifier
                    .background(NeonGreen, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Debug counter
        Text(
            text = "DBG: ${state.debugState}",
            style = MaterialTheme.typography.labelSmall,
            color = SuccessGreen,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Frequency range display
        val range = state.scanConfig.frequencyRange
        Text(
            text = "${formatMhz(range.startHz.toDouble())} – ${formatMhz(range.endHz.toDouble())}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Scanning indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (state.isScanning) NeonGreen else ErrorRed),
        )
    }
}

// ════════════════════════════════════════════════════════
// LEFT CONTROL PANEL
// ════════════════════════════════════════════════════════
@Composable
private fun ControlPanel(
    state: SpectrumState,
    onRangeSelected: (com.watchoutrf.app.domain.model.FrequencyRange) -> Unit,
    onModeChanged: (com.watchoutrf.app.domain.model.ScanMode) -> Unit,
    onToggleMaxHold: () -> Unit,
    onStartStop: () -> Unit,
    onResetMaxHold: () -> Unit,
    onClearMarkers: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(DarkSurface)
            .border(width = 1.dp, color = GridLine, shape = RoundedCornerShape(0.dp))
            .padding(12.dp),
    ) {
        // Frequency range presets
        FrequencyRangeSelector(
            currentRange = state.scanConfig.frequencyRange,
            onRangeSelected = onRangeSelected,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = GridLine, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Scan mode selector
        ScanModeSelector(
            currentMode = state.scanConfig.scanMode,
            maxHoldEnabled = state.scanConfig.maxHoldEnabled,
            onModeChanged = onModeChanged,
            onToggleMaxHold = onToggleMaxHold,
        )

        Spacer(modifier = Modifier.weight(1f))

        // ─── Action Buttons ───
        // Start / Stop button
        Button(
            onClick = onStartStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isScanning) ErrorRed.copy(alpha = 0.15f) else NeonGreen,
                contentColor = if (state.isScanning) ErrorRed else DeepBlack,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.isScanning) {
                        Modifier.border(1.dp, ErrorRed, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                ),
        ) {
            Icon(
                imageVector = if (state.isScanning) Icons.Default.Clear else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (state.isScanning) "STOP" else "SCAN",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Reset Max Hold button (visible when max hold is on)
        if (state.scanConfig.maxHoldEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetMaxHold,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberYellow),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberYellow.copy(alpha = 0.5f)),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset Hold",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Clear markers button (visible when markers exist)
        if (state.markers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearMarkers,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GridLine),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Clear Markers",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ════════════════════════════════════════════════════════
// RIGHT SPECTRUM AREA
// ════════════════════════════════════════════════════════
@Composable
private fun SpectrumArea(
    state: SpectrumState,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    onTapSpectrum: (Float) -> Unit,
) {
    val config = state.scanConfig

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 2.dp),
    ) {
        // ─── Spectrum Plot + Amplitude Scale ───
        Row(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth(),
        ) {
            // Amplitude scale (left y-axis labels)
            AmplitudeScale(
                referenceLevel = config.referenceLevel,
                dynamicRange = config.dynamicRange,
                textMeasurer = textMeasurer,
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
            )

            // Spectrum plot with marker overlay + tap gesture
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val normalizedX = offset.x / size.width.toFloat()
                            onTapSpectrum(normalizedX)
                        }
                    },
            ) {
                SpectrumPlot(
                    spectrumData = state.currentSpectrum,
                    maxHoldData = state.maxHoldSpectrum,
                    referenceLevel = config.referenceLevel,
                    dynamicRange = config.dynamicRange,
                    modifier = Modifier.fillMaxSize(),
                )

                MarkerOverlay(
                    markers = state.allMarkers,
                    startHz = config.frequencyRange.startHz,
                    endHz = config.frequencyRange.endHz,
                    referenceLevel = config.referenceLevel,
                    dynamicRange = config.dynamicRange,
                    textMeasurer = textMeasurer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ─── Frequency Ruler ───
        FrequencyRuler(
            startHz = config.frequencyRange.startHz,
            endHz = config.frequencyRange.endHz,
            textMeasurer = textMeasurer,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(start = 48.dp), // align with spectrum plot (past amplitude scale)
        )

        // ─── Waterfall Plot ───
        Row(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth(),
        ) {
            // Spacer matching amplitude scale width
            Spacer(modifier = Modifier.width(48.dp))

            WaterfallPlot(
                waterfallHistory = state.waterfallHistory,
                referenceLevel = config.referenceLevel,
                dynamicRange = config.dynamicRange,
                colorMapType = 0,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// BOTTOM MARKER BAR
// ════════════════════════════════════════════════════════
@Composable
private fun BottomMarkerBar(state: SpectrumState) {
    val allMarkers = state.allMarkers

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(28.dp),
    ) {
        if (allMarkers.isEmpty()) {
            Text(
                text = "Tap spectrum to add markers",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(allMarkers) { marker ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Marker color indicator dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(markerColorToComposeColor(marker.color)),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${marker.label}: ${formatMhz(marker.frequencyHz)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.1f", marker.amplitudeDbm)} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ════════════════════════════════════════════════════════

private fun formatMhz(hz: Double): String {
    val mhz = hz / 1_000_000.0
    return if (mhz >= 1000) {
        "${String.format("%.2f", mhz / 1000)} GHz"
    } else {
        "${String.format("%.1f", mhz)} MHz"
    }
}

private fun markerColorToComposeColor(color: com.watchoutrf.app.domain.model.MarkerColor): androidx.compose.ui.graphics.Color {
    return when (color) {
        com.watchoutrf.app.domain.model.MarkerColor.RED -> ErrorRed
        com.watchoutrf.app.domain.model.MarkerColor.GREEN -> NeonGreen
        com.watchoutrf.app.domain.model.MarkerColor.BLUE -> CyanBright
        com.watchoutrf.app.domain.model.MarkerColor.YELLOW -> AmberYellow
        com.watchoutrf.app.domain.model.MarkerColor.ORANGE -> WarningOrange
        com.watchoutrf.app.domain.model.MarkerColor.CYAN -> CyanDim
        com.watchoutrf.app.domain.model.MarkerColor.MAGENTA -> androidx.compose.ui.graphics.Color(0xFFFF00FF)
        com.watchoutrf.app.domain.model.MarkerColor.WHITE -> TextPrimary
    }
}
