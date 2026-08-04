package com.example.wardrobeapp.ui.outfit

import androidx.compose.ui.geometry.Offset
import com.example.wardrobeapp.domain.model.ClothingItem

data class DraggableClothing(
    val item: ClothingItem,
    val position: Offset
)

data class OutfitExporterUiState(
    val clothing: List<DraggableClothing> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSharing: Boolean = false,
    val saveMessage: String? = null,
    val errorMessage: String? = null
)