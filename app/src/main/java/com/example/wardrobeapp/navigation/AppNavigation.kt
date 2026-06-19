package com.example.wardrobeapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.wardrobeapp.ui.home.HomeScreen
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.calendar.CalendarScreen
import com.example.wardrobeapp.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
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
                onNavigateToEditItem = { itemId: String ->
                    navController.navigate(Screen.EditItem.createRoute(itemId)) 
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AddItem.route) {
            // Updated placeholder for AddItemScreen with a Back button
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.Text("Add Item Screen stub")
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = { navController.popBackStack() }) {
                    androidx.compose.material3.Text("Back to Wardrobe")
                }
            }
        }
        composable(Screen.EditItem.route) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            // Updated placeholder for EditItemScreen with a Back button
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.Text("Edit Item Screen stub for ID: $itemId")
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = { navController.popBackStack() }) {
                    androidx.compose.material3.Text("Back")
                }
            }
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
