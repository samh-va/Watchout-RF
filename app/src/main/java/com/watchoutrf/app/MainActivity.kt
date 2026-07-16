package com.watchoutrf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.watchoutrf.app.data.usb.UsbDeviceManager
import com.watchoutrf.app.ui.navigation.AppNavigation
import com.watchoutrf.app.ui.theme.WatchoutRFTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var usbDeviceManager: UsbDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbDeviceManager.startMonitoring()
        enableEdgeToEdge()
        setContent {
            WatchoutRFTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usbDeviceManager.stopMonitoring()
    }
}
