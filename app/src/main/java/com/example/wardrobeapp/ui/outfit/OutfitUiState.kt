package com.example.wardrobeapp.ui.outfit

import com.example.wardrobeapp.domain.model.Outfit

data class OutfitUiState(
    val generatedOutfit: Outfit? = null,
    val isLoading: Boolean = false
)
