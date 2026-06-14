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
            HomeScreen(
                onNavigateToWardrobe = { navController.navigate(Screen.Wardrobe.route) },
                onNavigateToOutfitGenerator = { navController.navigate(Screen.OutfitGenerator.route) },
                onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onNavigateToAddItem = { navController.navigate(Screen.AddItem.route) },
                onNavigateToEditItem = { itemId -> 
                    navController.navigate(Screen.EditItem.createRoute(itemId)) 
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AddItem.route) {
            // Placeholder for AddItemScreen (Hermela's task)
            androidx.compose.material3.Text("Add Item Screen stub")
        }
        composable(Screen.EditItem.route) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            // Placeholder for EditItemScreen (Hermela's task)
            androidx.compose.material3.Text("Edit Item Screen stub for ID: $itemId")
        }
        composable(Screen.OutfitGenerator.route) {
            OutfitGeneratorScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
