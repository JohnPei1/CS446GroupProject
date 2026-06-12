package com.example.wardrobeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wardrobeapp.navigation.AppNavigation
import com.example.wardrobeapp.navigation.Screen

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

@Composable
fun WardrobeApp() {
    val navController = rememberNavController()
    
    val navItems = listOf(
        NavItem(Screen.Home, "Home", Icons.Default.Home),
        NavItem(Screen.Wardrobe, "Wardrobe", Icons.Default.Checkroom),
        NavItem(Screen.OutfitGenerator, "Outfits", Icons.Default.AutoAwesome),
        NavItem(Screen.Calendar, "Calendar", Icons.Default.CalendarMonth),
        NavItem(Screen.Settings, "Settings", Icons.Default.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        // highlight selected icon
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                // Avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    // Save scrolling position (state)
                                    saveState = true
                                }
                                // Avoid opening the same destination twice
                                launchSingleTop = true
                                // Allow restoring the state (scroll position)
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
