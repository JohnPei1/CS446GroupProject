package com.example.wardrobeapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.wardrobeapp.ui.wardrobe.WardrobeScreen
import com.example.wardrobeapp.ui.outfit.OutfitGeneratorScreen
import com.example.wardrobeapp.ui.outfit.OutfitViewModel
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val wardrobeViewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModel.Factory)
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
                onBack = { navController.popBackStack() },
                viewModel = wardrobeViewModel
            )
        }
        composable(
            route = Screen.OutfitGenerator.route,
            arguments = listOf(
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getLong("date")?.takeIf { it != -1L }
            OutfitGeneratorScreen(
                date = date,
                viewModel = viewModel(factory = OutfitViewModel.provideFactory(context))
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPlanner = { date ->
                    navController.navigate(Screen.OutfitGenerator.createRoute(date))
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AddItem.route){
            AddItemScreen(
                onExitClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.EditItem.route) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
            EditItemScreen(
                itemId,
                wardrobeViewModel,
                onExitClick = {
                    navController.popBackStack()
                },
            )
        }
    }
}
