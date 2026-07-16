package com.watchoutrf.app.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchoutrf.app.domain.model.FrequencyRange
import com.watchoutrf.app.ui.theme.*

@Composable
fun FrequencyRangeSelector(
    currentRange: FrequencyRange,
    onRangeSelected: (FrequencyRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 180.dp)) {
        Text(
            text = "FREQUENCY",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = TextDim,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(FrequencyRange.PRESETS) { preset ->
                val isSelected = currentRange == preset
                val shape = RoundedCornerShape(8.dp)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (isSelected) {
                                Modifier.background(NeonGreen.copy(alpha = 0.15f), shape)
                                    .border(1.dp, NeonGreen, shape)
                            } else {
                                Modifier.background(DarkSurfaceVariant, shape)
                            }
                        )
                        .clickable { onRangeSelected(preset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .widthIn(min = 56.dp),
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NeonGreen else TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFrequencySpan(preset.startHz, preset.endHz),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = if (isSelected) NeonGreen.copy(alpha = 0.7f) else TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatFrequencySpan(startHz: Long, endHz: Long): String {
    val startMhz = startHz / 1_000_000.0
    val endMhz = endHz / 1_000_000.0
    return if (startMhz >= 1000) {
        "${String.format("%.1f", startMhz / 1000)}-${String.format("%.1f", endMhz / 1000)}G"
    } else {
        "${startMhz.toInt()}-${endMhz.toInt()}M"
    }
}
