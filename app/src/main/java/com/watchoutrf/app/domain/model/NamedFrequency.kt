package com.watchoutrf.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "named_frequencies")
data class NamedFrequency(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val frequencyHz: Long,
    val colorHex: String = "#FF6B00", // Default to an orange color
    val isVisible: Boolean = true
)
