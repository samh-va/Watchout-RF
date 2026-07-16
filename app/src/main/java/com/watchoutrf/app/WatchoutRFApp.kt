package com.watchoutrf.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WatchoutRFApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }

    companion object {
        const val TAG = "WatchoutRF"
    }
}
