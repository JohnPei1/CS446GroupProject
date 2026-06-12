package com.example.wardrobeapp.ui.outfit

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.wardrobeapp.domain.model.Outfit

@Composable
fun OutfitPreview(outfit: Outfit) {
    Text(text = "Previewing Outfit: ${outfit.name}")
}
