package com.watchoutrf.app.ui.components.spectrum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.watchoutrf.app.domain.model.NamedFrequency
import com.watchoutrf.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyManagerSheet(
    frequencies: List<NamedFrequency>,
    onDismiss: () -> Unit,
    onAddFrequency: (name: String, freqHz: Long) -> Unit,
    onDeleteFrequency: (NamedFrequency) -> Unit,
    onToggleVisibility: (NamedFrequency) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextDim) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Frequencies",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Frequency", tint = BrandOrange)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (frequencies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No frequencies saved yet.", color = TextDim)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(frequencies) { freq ->
                        FrequencyItem(
                            freq = freq,
                            onDelete = { onDeleteFrequency(freq) },
                            onToggle = { onToggleVisibility(freq) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp)) // Padding for bottom edge
        }
    }

    if (showAddDialog) {
        AddFrequencyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, hz ->
                onAddFrequency(name, hz)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FrequencyItem(
    freq: NamedFrequency,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(DarkBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = try { Color(android.graphics.Color.parseColor(freq.colorHex)) } catch (e: Exception) { BrandOrange }
        
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = freq.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(
                text = "${freq.frequencyHz / 1_000_000.0} MHz",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (freq.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle Visibility",
                tint = if (freq.isVisible) TextPrimary else TextDim
            )
        }
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BrandRed)
        }
    }
}

@Composable
private fun AddFrequencyDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, freqHz: Long) -> Unit
) {
    var name    by remember { mutableStateOf("") }
    var freqStr by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Add Frequency", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name (e.g., PSM 1000 Ch1)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandOrange,
                        cursorColor = BrandOrange
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = freqStr,
                    onValueChange = { freqStr = it; error = null },
                    label = { Text("Frequency (MHz), e.g. 523.300") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandOrange,
                        cursorColor = BrandOrange
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Accept both "." and "," as decimal separator (locale-safe)
                    val normalized = freqStr.trim().replace(",", ".")
                    val mhz = normalized.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Name is required"
                        mhz == null    -> error = "Enter a valid frequency (e.g. 523.300)"
                        mhz <= 0       -> error = "Frequency must be greater than 0"
                        else -> onConfirm(name.trim(), (mhz * 1_000_000).toLong())
                    }
                }
            ) {
                Text("Save", color = BrandOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
