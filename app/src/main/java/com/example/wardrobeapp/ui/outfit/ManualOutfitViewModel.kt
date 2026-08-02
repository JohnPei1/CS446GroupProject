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
        val outfit = buildValidatedOutfit() ?: return
        viewModelScope.launch {
            runCatching { outfitRepository.saveOutfit(outfit) }
                .onSuccess { _uiState.value = _uiState.value.copy(isDone = true, error = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Couldn't save the outfit.") }
        }
    }

    /**
     * Plans the built outfit for [date] (saving it first -- a plan references a saved outfit).
     * If that day already has an outfit, the request is parked in
     * [ManualOutfitUiState.pendingSchedule] for the user to confirm the replacement.
     */
    fun requestSchedule(date: Long) {
        if (buildValidatedOutfit() == null) return
        viewModelScope.launch {
            val day = normalizeToUtcDay(date)
            val existing = runCatching { outfitRepository.getScheduledOutfit(day) }.getOrNull()
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    pendingSchedule = PendingSchedule(day, existing.name)
                )
            } else {
                performSchedule(day)
            }
        }
    }

    fun confirmPendingSchedule() {
        val pending = _uiState.value.pendingSchedule ?: return
        _uiState.value = _uiState.value.copy(pendingSchedule = null)
        viewModelScope.launch { performSchedule(pending.date) }
    }

    fun dismissPendingSchedule() {
        _uiState.value = _uiState.value.copy(pendingSchedule = null)
    }

    private suspend fun performSchedule(day: Long) {
        val outfit = buildValidatedOutfit() ?: return
        runCatching {
            val id = outfitRepository.saveOutfit(outfit)
            outfitRepository.scheduleOutfit(id, day)
            // Wearing it today also records the items as worn (feeds anti-repetition scoring).
            if (day == normalizeToUtcDay(System.currentTimeMillis())) {
                wardrobeRepository.markItemsWorn(outfit.items.map { it.id })
            }
        }.onSuccess {
            _uiState.value = _uiState.value.copy(isDone = true, error = null)
        }.onFailure {
            _uiState.value = _uiState.value.copy(error = "Couldn't plan the outfit.")
        }
    }

    /** A complete outfit needs a top and a bottom; sets an error and returns null otherwise. */
    private fun buildValidatedOutfit(): Outfit? {
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
            return null
        }
        return Outfit(
            name = state.outfitName.trim().ifBlank { DEFAULT_OUTFIT_NAME },
            items = selected,
            note = "Created manually"
        )
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
