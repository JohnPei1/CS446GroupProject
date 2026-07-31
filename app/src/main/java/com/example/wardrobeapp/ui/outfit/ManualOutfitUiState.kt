package com.example.wardrobeapp.ui.outfit

import com.example.wardrobeapp.domain.model.ClothingItem

data class ManualOutfitUiState(
    val items: List<ClothingItem> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val outfitName: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val selectedItems: List<ClothingItem> get() = items.filter { it.id in selectedItemIds }
}
