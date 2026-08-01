package com.watchoutrf.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.watchoutrf.app.domain.model.NamedFrequency
import kotlinx.coroutines.flow.Flow

@Dao
interface NamedFrequencyDao {

    @Query("SELECT * FROM named_frequencies ORDER BY frequencyHz ASC")
    fun getAllFrequencies(): Flow<List<NamedFrequency>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frequency: NamedFrequency)

    @Update
    suspend fun update(frequency: NamedFrequency)

    @Delete
    suspend fun delete(frequency: NamedFrequency)
}
