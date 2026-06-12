package com.example.wardrobeapp.navigation

/**
 * Defines the navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Wardrobe : Screen("wardrobe")
    object AddItem : Screen("add_item")
    object EditItem : Screen("edit_item/{itemId}") {
        fun createRoute(itemId: String) = "edit_item/$itemId"
    }
    object OutfitGenerator : Screen("outfit_generator")
    object Calendar : Screen("calendar")
    object Settings : Screen("settings")
}
