package com.watchoutrf.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.watchoutrf.app.domain.model.NamedFrequency

@Database(entities = [NamedFrequency::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun namedFrequencyDao(): NamedFrequencyDao
}
