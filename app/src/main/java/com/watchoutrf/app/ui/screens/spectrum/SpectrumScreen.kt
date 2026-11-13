package com.watchoutrf.app.ui.screens.spectrum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import com.watchoutrf.app.domain.model.MarkerColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
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

    var showFrequencyManager by remember { mutableStateOf(false) }
    var exportMessage      by remember { mutableStateOf<String?>(null) }

    // File picker for WWB7 export
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportMessage = viewModel.exportWwb(uri) ?: "Exportado correctamente."
        }
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
            onResolutionChanged = viewModel::updateNumBins,
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
                onShowFrequencyManager = { showFrequencyManager = true },
                onColorChange = viewModel::updateMarkerColor,
                onUpdateMarkerLabel = viewModel::updateMarkerLabel,
                onExportWwb = {
                    exportLauncher.launch("WatchoutRF_scan.csv")
                },
            )

            // ─── RIGHT SPECTRUM AREA ───
            SpectrumArea(
                state = state,
                textMeasurer = textMeasurer,
                onMarkerUpdate = viewModel::updateActiveMarker,
            )
        }

        // ═══════════════════════════════════════════════
        // BOTTOM MARKER BAR
        // ═══════════════════════════════════════════════
        BottomMarkerBar(state = state)
    }

    if (showFrequencyManager) {
        FrequencyManagerSheet(
            frequencies = state.namedFrequencies,
            onDismiss = { showFrequencyManager = false },
            onAddFrequency = { name, freqHz -> viewModel.addNamedFrequency(name, freqHz) },
            onDeleteFrequency = viewModel::deleteNamedFrequency,
            onToggleVisibility = viewModel::toggleFrequencyVisibility
        )
    }

    exportMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(if (msg.startsWith("Exportado")) "Export exitoso" else "Error de export") },
            text  = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) { Text("OK") }
            },
        )
    }
}

// ════════════════════════════════════════════════════════
// TOP STATUS BAR
// ════════════════════════════════════════════════════════
@Composable
private fun TopStatusBar(
    state: SpectrumState,
    onNavigateBack: () -> Unit,
    onResolutionChanged: (Int) -> Unit,
) {
    val resolutionOptions = listOf(1024, 2048, 4096)

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

        Spacer(modifier = Modifier.weight(1f))

        // Resolution selector
        Text(
            text = "RES:",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        resolutionOptions.forEach { bins ->
            val isSelected = state.scanConfig.numBins == bins
            Text(
                text = "$bins",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (isSelected) NeonGreen else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isSelected) Modifier.background(NeonGreen.copy(alpha = 0.15f))
                        else Modifier
                    )
                    .clickable { onResolutionChanged(bins) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

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
    onShowFrequencyManager: () -> Unit,
    onColorChange: (Int, MarkerColor) -> Unit,
    onUpdateMarkerLabel: (Int, String) -> Unit,
    onExportWwb: () -> Unit,
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

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = GridLine, thickness = 1.dp)

        // ─── Custom Markers Scroller ───
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.markers) { marker ->
                AndroidMarkerItem(
                    marker = marker,
                    onColorChange = onColorChange,
                    onUpdateLabel = onUpdateMarkerLabel,
                )
            }
        }

        // ─── Action Buttons ───
        
        OutlinedButton(
            onClick = onShowFrequencyManager,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOrange),
            border = BorderStroke(1.dp, BrandOrange),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("📡 Frequencies", style = MaterialTheme.typography.labelMedium)
        }
        
        Spacer(modifier = Modifier.height(8.dp))

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

        // ─── Export WWB7 ───
        val hasData = state.currentSpectrum != null || state.maxHoldSpectrum != null
        HorizontalDivider(color = GridLine, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onExportWwb,
            enabled = hasData,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = androidx.compose.ui.graphics.Color(0xFFFF6B35),
                disabledContentColor = TextSecondary,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = if (hasData)
                    androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF6B35).copy(alpha = 0.6f))
                else
                    androidx.compose.ui.graphics.SolidColor(GridLine),
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Export WWB7",
                style = MaterialTheme.typography.labelSmall,
            )
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
    onMarkerUpdate: (Float) -> Unit,
) {
    val config = state.scanConfig

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 2.dp),
    ) {
        // ─── TV Channel Band Plan ───
        TvChannelBandPlan(
            startHz = config.frequencyRange.startHz,
            endHz   = config.frequencyRange.endHz,
            textMeasurer = textMeasurer,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(start = 48.dp),
        )

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
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val normalizedX = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onMarkerUpdate(normalizedX)
                            
                            do {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: break
                                val nx = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onMarkerUpdate(nx)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
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

                NamedFrequenciesOverlay(
                    frequencies = state.namedFrequencies,
                    startHz = config.frequencyRange.startHz,
                    endHz = config.frequencyRange.endHz,
                    textMeasurer = textMeasurer,
                    modifier = Modifier.fillMaxSize()
                )

                if (state.activeMarkerX != null) {
                    ActiveMarkerOverlay(
                        normalizedX = state.activeMarkerX,
                        spectrumData = state.currentSpectrum,
                        startHz = config.frequencyRange.startHz,
                        endHz = config.frequencyRange.endHz,
                        referenceLevel = config.referenceLevel,
                        dynamicRange = config.dynamicRange,
                        textMeasurer = textMeasurer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
    val activeX = state.activeMarkerX
    val autoPeaks = state.autoDetectedPeaks

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(28.dp),
    ) {
        // Active marker info (left side)
        if (activeX != null) {
            val config = state.scanConfig
            val range = config.frequencyRange
            val freqHz = range.startHz + ((range.endHz - range.startHz) * activeX).toLong()
            val spectrum = state.currentSpectrum
            val ampDbm = if (spectrum != null && spectrum.isNotEmpty()) {
                val bin = (activeX * (spectrum.size - 1)).toInt().coerceIn(0, spectrum.size - 1)
                spectrum[bin]
            } else null

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFF6B00)),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatFreqForMarker(freqHz),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF6B00),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (ampDbm != null) "${String.format("%.1f", ampDbm)} dBm" else "--",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
            )
        } else {
            Text(
                text = "Tap and drag on spectrum",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }

        // Separator + auto-detected peaks only (right side)
        if (autoPeaks.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(GridLine),
            )
            Spacer(modifier = Modifier.width(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(autoPeaks) { marker ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

private fun formatFreqForMarker(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return String.format("%.3f MHz", mhz)
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

@Composable
private fun ActiveMarkerOverlay(
    normalizedX: Float,
    spectrumData: FloatArray?,
    startHz: Long,
    endHz: Long,
    referenceLevel: Float,
    dynamicRange: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val x = normalizedX * w
        
        // Vertical crosshair line
        drawLine(
            color = Color(0xFFFF6B00),  // Orange
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))
        )
        
        // Calculate frequency and amplitude at this position
        val freqHz = startHz + ((endHz - startHz) * normalizedX).toLong()
        val amplitude = if (spectrumData != null && spectrumData.isNotEmpty()) {
            val bin = (normalizedX * (spectrumData.size - 1)).toInt().coerceIn(0, spectrumData.size - 1)
            spectrumData[bin]
        } else null
        
        // Draw small diamond at the amplitude point
        if (amplitude != null) {
            val y = (h * (referenceLevel - amplitude) / dynamicRange).coerceIn(0f, h)
            val diamondSize = 5.dp.toPx()
            val diamondPath = Path().apply {
                moveTo(x, y - diamondSize)
                lineTo(x + diamondSize, y)
                lineTo(x, y + diamondSize)
                lineTo(x - diamondSize, y)
                close()
            }
            drawPath(diamondPath, color = Color(0xFFFF6B00))
        }
        
        // Draw frequency label box at the top
        val freqText = formatFreqForMarker(freqHz)
        val ampText = if (amplitude != null) String.format("%.1f dBm", amplitude) else "--"
        val label = "$freqText\n$ampText"
        
        val textLayoutResult = textMeasurer.measure(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        )
        
        // Position the label box - flip sides if near the edge
        val labelWidth = textLayoutResult.size.width.toFloat()
        val labelHeight = textLayoutResult.size.height.toFloat()
        val padding = 4.dp.toPx()
        val labelX = if (x + labelWidth + padding * 3 > w) {
            x - labelWidth - padding * 3  // Show on left side
        } else {
            x + padding  // Show on right side
        }
        val labelY = 4.dp.toPx()
        
        // Background rectangle
        drawRoundRect(
            color = Color(0xCC000000),
            topLeft = Offset(labelX, labelY),
            size = Size(labelWidth + padding * 2, labelHeight + padding * 2),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        // Border
        drawRoundRect(
            color = Color(0xFFFF6B00),
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

// ════════════════════════════════════════════════════════
// CONTROL PANEL MARKER ITEM (inline editing + color picker)
// ════════════════════════════════════════════════════════
@Composable
private fun AndroidMarkerItem(
    marker: com.watchoutrf.app.domain.model.Marker,
    onColorChange: (Int, MarkerColor) -> Unit,
    onUpdateLabel: (Int, String) -> Unit,
) {
    val colorCycle = MarkerColor.entries
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(marker.label) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isEditing) {
                BasicTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    textStyle = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.DarkGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save label",
                    tint = NeonGreen,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            onUpdateLabel(marker.id, editValue)
                            isEditing = false
                        },
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = marker.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp),
                        color = markerColorToComposeColor(marker.color),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit label",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                isEditing = true
                                editValue = marker.label
                            },
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatMhz(marker.frequencyHz),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // Explicit color palette — tap the color you want
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            colorCycle.forEach { colorEnum ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(markerColorToComposeColor(colorEnum))
                        .clickable { onColorChange(marker.id, colorEnum) }
                        .then(
                            if (marker.color == colorEnum) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(50))
                            } else Modifier,
                        ),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// TV CHANNEL BAND PLAN
// Shows UHF TV channel numbers (14–51) above the spectrum.
// Each channel is 6 MHz wide, starting at 470 MHz.
// ════════════════════════════════════════════════════════
@Composable
private fun TvChannelBandPlan(
    startHz: Long,
    endHz: Long,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val chStart = 470_000_000L
    val chWidth = 6_000_000L
    val firstCh = 14
    val lastCh  = 51

    val spanHz = endHz - startHz
    if (spanHz <= 0) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bgAlt1      = androidx.compose.ui.graphics.Color(0xFF1A1A2E)
        val bgAlt2      = androidx.compose.ui.graphics.Color(0xFF16213E)
        val borderCol   = androidx.compose.ui.graphics.Color(0xFF2A2A4A)
        val textCol     = androidx.compose.ui.graphics.Color(0xFF7A7A9E)
        val occupiedCol = androidx.compose.ui.graphics.Color(0x22FF6B35)

        for (ch in firstCh..lastCh) {
            val chFreqStart = chStart + (ch - firstCh) * chWidth
            val chFreqEnd   = chFreqStart + chWidth

            if (chFreqEnd < startHz || chFreqStart > endHz) continue

            val x1 = ((chFreqStart - startHz).toFloat() / spanHz * w).coerceIn(0f, w)
            val x2 = ((chFreqEnd   - startHz).toFloat() / spanHz * w).coerceIn(0f, w)
            val chW = x2 - x1
            if (chW < 1f) continue

            val bg = if ((ch - firstCh) % 2 == 0) bgAlt1 else bgAlt2
            drawRect(color = bg,          topLeft = androidx.compose.ui.geometry.Offset(x1, 0f), size = androidx.compose.ui.geometry.Size(chW, h))
            drawRect(color = occupiedCol, topLeft = androidx.compose.ui.geometry.Offset(x1, 0f), size = androidx.compose.ui.geometry.Size(chW, h))
            drawLine(color = borderCol,   start = androidx.compose.ui.geometry.Offset(x2, 0f), end = androidx.compose.ui.geometry.Offset(x2, h), strokeWidth = 0.5.dp.toPx())

            if (chW > 18.dp.toPx()) {
                val label = "$ch"
                val measured = textMeasurer.measure(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        color = textCol,
                        fontSize = 8.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                )
                val tx = x1 + (chW - measured.size.width) / 2f
                val ty = (h - measured.size.height) / 2f
                drawText(measured, topLeft = androidx.compose.ui.geometry.Offset(tx.coerceAtLeast(x1), ty))
            }
        }

        drawLine(color = borderCol, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, 0f), strokeWidth = 0.5.dp.toPx())
        drawLine(color = borderCol, start = androidx.compose.ui.geometry.Offset(0f, h), end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = 0.5.dp.toPx())
    }
}
