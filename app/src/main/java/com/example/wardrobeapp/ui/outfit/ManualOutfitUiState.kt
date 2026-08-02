package com.example.wardrobeapp.ui.outfit

import com.example.wardrobeapp.domain.model.ClothingItem

data class ManualOutfitUiState(
    val items: List<ClothingItem> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val outfitName: String = "",
    val isLoading: Boolean = true,
    /** True once the outfit has been saved (or saved + planned) -- the screen then closes. */
    val isDone: Boolean = false,
    /** Set when scheduling needs the user to confirm replacing an existing day plan. */
    val pendingSchedule: PendingSchedule? = null,
    val error: String? = null
) {
    val selectedItems: List<ClothingItem> get() = items.filter { it.id in selectedItemIds }
}
