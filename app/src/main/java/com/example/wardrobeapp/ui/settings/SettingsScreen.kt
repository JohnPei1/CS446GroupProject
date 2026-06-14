package com.example.wardrobeapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column {
        Text(text = "Settings Screen")
        Button(onClick = onBack) { Text("Back") }
    }
}
