package com.example.wardrobeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.wardrobeapp.ui.home.HomeScreen
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.calendar.CalendarScreen
import com.example.wardrobeapp.ui.settings.SettingsScreen
import com.example.wardrobeapp.ui.wardrobe.AddItemScreen

@Composable
fun AppNavigation(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Wardrobe.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onAddItemClick = {
                    navController.navigate(Screen.AddItem.route)
                }
            )
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
        composable(Screen.AddItem.route){
            AddItemScreen()
        }
    }
}
