package com.watchoutrf.app.di

import android.content.Context
import com.watchoutrf.app.data.usb.UsbDeviceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUsbDeviceManager(
        @ApplicationContext context: Context,
    ): UsbDeviceManager {
        return UsbDeviceManager(context)
    }
}
