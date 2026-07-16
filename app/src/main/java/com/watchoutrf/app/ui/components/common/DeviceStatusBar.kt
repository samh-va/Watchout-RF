package com.watchoutrf.app.ui.components.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchoutrf.app.data.usb.UsbDeviceInfo
import com.watchoutrf.app.ui.theme.*

/**
 * Status bar showing SDR device connection status and info.
 * Displayed at the top of the screen.
 */
@Composable
fun DeviceStatusBar(
    isConnected: Boolean,
    isRequesting: Boolean,
    statusText: String,
    deviceInfo: UsbDeviceInfo?,
    nativeVersion: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App title
            Text(
                text = "WatchoutRF",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                ),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Native version badge (if loaded)
            if (nativeVersion != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Text(
                        text = "NDK v$nativeVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Connection status pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isConnected -> NeonGreen.copy(alpha = 0.12f)
                    isRequesting -> AmberYellow.copy(alpha = 0.12f)
                    else -> DarkSurfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConnectionIndicator(
                        isConnected = isConnected,
                        isRequesting = isRequesting,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isConnected -> NeonGreen
                            isRequesting -> AmberYellow
                            else -> TextSecondary
                        },
                    )
                }
            }
        }
    }

    // Show device details when connected
    AnimatedVisibility(
        visible = isConnected && deviceInfo != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        if (deviceInfo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailChip(
                        label = "Device",
                        value = deviceInfo.displayName,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    DetailChip(
                        label = "Range",
                        value = deviceInfo.frequencyRange,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    DetailChip(
                        label = "VID:PID",
                        value = "0x${deviceInfo.vendorId.toString(16).uppercase()}:0x${deviceInfo.productId.toString(16).uppercase()}",
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = TextSecondary,
        )
    }
}
