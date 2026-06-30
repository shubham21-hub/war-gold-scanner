package com.wgs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wgs.app.ui.home.HomeScreen
import com.wgs.app.ui.home.HomeViewModel
import com.wgs.app.ui.records.RecordsScreen
import com.wgs.app.ui.settings.SettingsScreen
import com.wgs.app.ui.theme.WGSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WGSTheme {
                WGSNavigation()
            }
        }
    }
}

@Composable
fun WGSNavigation() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val warId by homeViewModel.warId.collectAsStateWithLifecycle(initialValue = "")

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToRecords = { navController.navigate("records") },
                onNavigateToSettings = { navController.navigate("settings") },
                viewModel = homeViewModel
            )
        }
        composable("records") {
            RecordsScreen(
                warId = warId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel
            )
        }
    }
}
