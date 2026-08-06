package com.example.wardrobeapp.ui.outfit

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OutfitExporterViewModel(
    private val repository: WardrobeRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            OutfitExporterUiState()
        )

    val uiState: StateFlow<OutfitExporterUiState> =
        _uiState.asStateFlow()

    private var initializedIds: List<Long> =
        emptyList()

    fun initializeOutfit(
        clothingIds: List<Long>
    ) {
        if (initializedIds == clothingIds) {
            return
        }

        initializedIds = clothingIds

        viewModelScope.launch {

            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )

            try {

                val clothingItems =
                    clothingIds.mapNotNull { id ->
                        repository
                            .getItem(id)
                            .first()
                    }

                val clothing =
                    clothingItems.mapIndexed { index, item ->
                        DraggableClothing(
                            item = item,
                            position = Offset(
                                x = 100f + index * 150f,
                                y = 200f
                            )
                        )
                    }

                _uiState.value =
                    _uiState.value.copy(
                        clothing = clothing,
                        isLoading = false
                    )

            } catch (e: Exception) {

                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            e.message
                                ?: "Failed to load outfit"
                    )
            }
        }
    }

    fun moveClothing(
        item: ClothingItem,
        dragAmount: Offset
    ) {
        val updatedClothing =
            _uiState.value.clothing.map { clothingItem ->

                if (clothingItem.item.id == item.id) {
                    clothingItem.copy(
                        position =
                            clothingItem.position +
                                    dragAmount
                    )
                } else {
                    clothingItem
                }
            }

        _uiState.value =
            _uiState.value.copy(
                clothing = updatedClothing
            )
    }

    fun startSaving() {
        _uiState.value =
            _uiState.value.copy(
                isSaving = true,
                saveMessage = null,
                errorMessage = null
            )
    }

    fun saveCompleted() {
        _uiState.value =
            _uiState.value.copy(
                isSaving = false,
                saveMessage = "Outfit saved"
            )
    }

    fun saveFailed(
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                isSaving = false,
                errorMessage = message
            )
    }

    fun startSharing() {
        _uiState.value =
            _uiState.value.copy(
                isSharing = true,
                errorMessage = null
            )
    }

    fun sharingCompleted() {
        _uiState.value =
            _uiState.value.copy(
                isSharing = false
            )
    }

    fun sharingFailed(
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                isSharing = false,
                errorMessage = message
            )
    }

    fun clearMessages() {
        _uiState.value =
            _uiState.value.copy(
                saveMessage = null,
                errorMessage = null
            )
    }

    companion object {

        fun provideFactory(
            repository: WardrobeRepository
        ): androidx.lifecycle.ViewModelProvider.Factory {

            return object :
                androidx.lifecycle.ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {
                    return OutfitExporterViewModel(
                        repository
                    ) as T
                }
            }
        }
    }

}
