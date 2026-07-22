package com.reader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reader.app.library.LibraryScreen
import com.reader.app.settings.SettingsScreen
import com.reader.app.ui.theme.ReaderTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SETTINGS = "settings"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReaderTheme {
                ReaderApp()
            }
        }
    }
}

@Composable
private fun ReaderApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
