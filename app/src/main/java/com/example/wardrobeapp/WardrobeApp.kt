package com.example.wardrobeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.wardrobeapp.navigation.AppNavigation

@Composable
fun WardrobeApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        AppNavigation(modifier = Modifier.padding(innerPadding))
    }
}
