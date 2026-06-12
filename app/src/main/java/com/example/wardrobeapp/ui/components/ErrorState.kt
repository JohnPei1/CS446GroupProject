package com.example.wardrobeapp.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ErrorState(message: String) {
    Text(text = "Error: $message")
}
