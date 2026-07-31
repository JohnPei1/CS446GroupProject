package com.example.wardrobeapp.ui.outfit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.strategy.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManualOutfitViewModel(
    private val outfitRepository: OutfitRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualOutfitUiState())
    val uiState: StateFlow<ManualOutfitUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            wardrobeRepository.getAllItems().collect { items ->
                _uiState.value = _uiState.value.copy(items = items, isLoading = false)
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(outfitName = name)
    }

    fun toggleItem(item: ClothingItem) {
        val selected = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (item.id in selected) selected - item.id else selected + item.id,
            error = null
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItemIds = emptySet(), error = null)
    }

    fun save() {
        val state = _uiState.value
        val selected = state.selectedItems
        val missing = buildList {
            if (selected.none { it.category.equals(Category.TOPS, ignoreCase = true) }) add(Category.TOPS)
            if (selected.none { it.category.equals(Category.BOTTOMS, ignoreCase = true) }) add(Category.BOTTOMS)
        }
        if (missing.isNotEmpty()) {
            _uiState.value = state.copy(
                error = "Pick an item from: ${missing.joinToString(" and ")}. " +
                        "A complete outfit needs a top and a bottom."
            )
            return
        }

        viewModelScope.launch {
            outfitRepository.saveOutfit(
                Outfit(
                    name = state.outfitName.trim().ifBlank { DEFAULT_OUTFIT_NAME },
                    items = selected,
                    note = "Created manually"
                )
            )
            _uiState.value = _uiState.value.copy(isSaved = true, error = null)
        }
    }

    /** record selected items as worn */
    fun markWorn() {
        val selected = _uiState.value.selectedItems
        if (selected.isEmpty()) return
        viewModelScope.launch {
            wardrobeRepository.markItemsWorn(selected.map { it.id })
        }
    }

    companion object {
        private const val DEFAULT_OUTFIT_NAME = "My Outfit"

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return ManualOutfitViewModel(
                        outfitRepository = container.outfitRepository,
                        wardrobeRepository = container.wardrobeRepository
                    ) as T
                }
            }
    }
}
