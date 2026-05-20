package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.viewmodel.StudioViewModel

@Composable
fun AppNavigation(viewModel: StudioViewModel) {
    val navController = rememberNavController()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    val hasSeenWelcome by viewModel.hasSeenWelcome.collectAsStateWithLifecycle()

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val initialRoute = remember { if (hasSeenWelcome) "studio" else "welcome" }

    NavHost(navController = navController, startDestination = initialRoute) {
        composable("welcome") {
            val context = androidx.compose.ui.platform.LocalContext.current
            WelcomeScreen(
                onNavigateToStudio = {
                    viewModel.completeWelcome()
                    navController.navigate("studio") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }
        composable("studio") {
            StudioScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDownloads = { navController.navigate("downloads") }
            )
        }
        composable("downloads") {
            DownloadedDataScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
