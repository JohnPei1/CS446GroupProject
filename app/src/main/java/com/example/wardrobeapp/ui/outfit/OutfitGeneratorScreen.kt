package com.example.wardrobeapp.ui.outfit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun OutfitGeneratorScreen(onBack: () -> Unit) {
    Column {
        Text(text = "Outfit Generator Screen")
        Button(onClick = onBack) { Text("Back") }
    }
}
