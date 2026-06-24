package com.example.wardrobeapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.calendar.CalendarScreen
import com.example.wardrobeapp.ui.settings.SettingsScreen
import com.example.wardrobeapp.ui.wardrobe.AddItemScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.ui.wardrobe.EditItemScreen
import com.example.wardrobeapp.ui.wardrobe.WardrobeViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val wardrobeViewModel: WardrobeViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = Screen.OutfitGenerator.route,
        modifier = modifier
    ) {
        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onNavigateToAddItem = { navController.navigate(Screen.AddItem.route) },
                onNavigateToEditItem = { itemId: String ->
                    navController.navigate(Screen.EditItem.createRoute(itemId))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.OutfitGenerator.route) {
            OutfitGeneratorScreen()
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AddItem.route){
            AddItemScreen(
                onExitClick = {
                    navController.popBackStack()
                }
                //TODO save item to database
                //onSaveItem = {}
            )
        }
        composable(Screen.EditItem.route) {
            //Temporary ID
            val id = 1.toLong()
            EditItemScreen(
                id,
                wardrobeViewModel,
                onExitClick = {
                    navController.popBackStack()
                },
            )
        }

        }

}
