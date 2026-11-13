package com.watchoutrf.app.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchoutrf.app.ui.components.common.DeviceStatusBar
import com.watchoutrf.app.ui.theme.*
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSpectrum: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // USB monitoring is owned by MainActivity (started in onCreate, stopped in onDestroy).
    // It must NOT be stopped when leaving this screen: the Spectrum screen relies on
    // attach / detach events to switch between the dongle and demo mode.
    LaunchedEffect(Unit) {
        viewModel.startUsbMonitoring() // idempotent
    }

    // Load native version
    val nativeVersion = remember { viewModel.getNativeVersion() }

    // Sine wave animation for the decorative spectrum
    val infiniteTransition = rememberInfiniteTransition(label = "home")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
    ) {
        // Status bar with real device state
        DeviceStatusBar(
            isConnected = uiState.isDeviceConnected,
            isRequesting = uiState.isRequestingPermission,
            statusText = uiState.statusText,
            deviceInfo = uiState.deviceInfo,
            nativeVersion = nativeVersion,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Decorative animated spectrum wave
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2
                val points = 200

                val path = Path()
                var firstPoint = true

                for (i in 0..points) {
                    val x = (i.toFloat() / points) * width
                    val normalizedX = i.toFloat() / points
                    val amplitude = height * 0.3f *
                            (0.3f + 0.7f * sin(normalizedX * 6f + phase).coerceIn(0f, 1f))
                    val y = centerY + (sin(normalizedX * 12f + phase) * amplitude).toFloat()

                    if (firstPoint) {
                        path.moveTo(x, y)
                        firstPoint = false
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = if (uiState.isDeviceConnected) NeonGreen.copy(alpha = 0.7f)
                            else CyanBright.copy(alpha = 0.4f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main content card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Icon
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (uiState.isDeviceConnected) NeonGreen else TextSecondary,
                        modifier = Modifier.size(56.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title - changes based on state
                    Text(
                        text = when {
                            uiState.isDeviceConnected -> "Device Ready!"
                            uiState.isRequestingPermission -> "Allow USB Access"
                            uiState.isPermissionDenied -> "Permission Required"
                            uiState.errorMessage != null -> "Connection Error"
                            else -> "Connect SDR Dongle"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                uiState.isDeviceConnected -> NeonGreen
                                uiState.errorMessage != null -> ErrorRed
                                else -> TextPrimary
                            },
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description - changes based on state
                    Text(
                        text = when {
                            uiState.isDeviceConnected -> "${uiState.deviceInfo?.displayName} is connected.\nTap below to open the spectrum analyzer."
                            uiState.isRequestingPermission -> "Please allow USB access in the\npermission dialog."
                            uiState.isPermissionDenied -> "USB permission was denied.\nPlease reconnect the dongle and try again."
                            uiState.errorMessage != null -> uiState.errorMessage ?: "Unknown error"
                            else -> "Connect your RTL-SDR USB dongle to start\nanalyzing the RF spectrum."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        ),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action button - changes based on state
                    if (uiState.isDeviceConnected) {
                        Button(
                            onClick = onNavigateToSpectrum,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = DeepBlack,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(
                                text = "Open Spectrum Analyzer",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    } else if (uiState.isPermissionDenied || uiState.errorMessage != null) {
                        OutlinedButton(
                            onClick = { viewModel.retryConnection() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CyanBright,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Retry Connection")
                        }
                    }

                    // Supported devices section (only when disconnected)
                    if (!uiState.isDeviceConnected) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "SUPPORTED DEVICES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 2.sp,
                                        color = TextDim,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SupportedDeviceRow("RTL-SDR Blog V4", "24 \u2013 1766 MHz")
                                SupportedDeviceRow("RTL-SDR Blog V3", "24 \u2013 1766 MHz")
                                SupportedDeviceRow("Generic RTL2832U", "24 \u2013 1700 MHz")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Demo Mode Button
                        OutlinedButton(
                            onClick = onNavigateToSpectrum,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AmberYellow,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(
                                text = "Try Demo Mode",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "WatchoutRF v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }
    }
}

@Composable
private fun SupportedDeviceRow(name: String, range: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(NeonGreen),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = range,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
