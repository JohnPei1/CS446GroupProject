package com.example.wardrobeapp.ui.wardrobe

import com.example.wardrobeapp.domain.model.ClothingItem

data class WardrobeUiState(
    val items: List<ClothingItem> = emptyList(),
    val isLoading: Boolean = false
)
