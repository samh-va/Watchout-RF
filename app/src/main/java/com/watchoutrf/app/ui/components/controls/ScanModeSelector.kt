package com.watchoutrf.app.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchoutrf.app.domain.model.ScanMode
import com.watchoutrf.app.ui.theme.*

@Composable
fun ScanModeSelector(
    currentMode: ScanMode,
    maxHoldEnabled: Boolean,
    onModeChanged: (ScanMode) -> Unit,
    onToggleMaxHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "SCAN MODE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = TextDim,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ScanMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            val shape = RoundedCornerShape(8.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(CyanBright.copy(alpha = 0.08f), shape)
                                .border(1.dp, CyanBright, shape)
                        } else {
                            Modifier.background(DarkSurfaceVariant, shape)
                        }
                    )
                    .clickable { onModeChanged(mode) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = mode.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) CyanBright else TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Max Hold toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Max Hold",
                style = MaterialTheme.typography.labelMedium,
                color = if (maxHoldEnabled) AmberYellow else TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = maxHoldEnabled,
                onCheckedChange = { onToggleMaxHold() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurfaceVariant,
                    uncheckedBorderColor = GridLine,
                ),
            )
        }
    }
}
