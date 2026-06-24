package com.example.wardrobeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.wardrobeapp.ui.home.HomeScreen
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.calendar.CalendarScreen
import com.example.wardrobeapp.ui.settings.SettingsScreen
import com.example.wardrobeapp.ui.wardrobe.AddItemScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.domain.model.ClothingItem
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
        startDestination = Screen.Wardrobe.route,
        modifier = modifier
    ) {
        //dummy clothing item
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onEdit = {
                    navController.navigate(Screen.EditItem.route)
                },
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
