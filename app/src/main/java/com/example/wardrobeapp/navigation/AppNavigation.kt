package com.example.wardrobeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wardrobeapp.ui.home.HomeScreen
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.calendar.CalendarScreen
import com.example.wardrobeapp.ui.settings.SettingsScreen

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Wardrobe.route) {
            WardrobeScreen()
        }
        composable(Screen.OutfitGenerator.route) {
            OutfitGeneratorScreen()
        }
        composable(Screen.Calendar.route) {
            CalendarScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
