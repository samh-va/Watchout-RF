package com.watchoutrf.app.di

import android.content.Context
import androidx.room.Room
import com.watchoutrf.app.data.db.AppDatabase
import com.watchoutrf.app.data.db.NamedFrequencyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "watchoutrf_db"
        )
        // If the schema changes and there's no explicit migration,
        // drop and recreate the database instead of crashing silently.
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideNamedFrequencyDao(database: AppDatabase): NamedFrequencyDao {
        return database.namedFrequencyDao()
    }
}
