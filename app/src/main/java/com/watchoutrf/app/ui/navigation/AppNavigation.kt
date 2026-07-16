package com.watchoutrf.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watchoutrf.app.ui.screens.home.HomeScreen
import com.watchoutrf.app.ui.screens.spectrum.SpectrumScreen
import com.watchoutrf.app.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SPECTRUM = "spectrum"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSpectrum = {
                    navController.navigate(Routes.SPECTRUM)
                },
            )
        }
        composable(Routes.SPECTRUM) {
            SpectrumScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
