package com.example.wardrobeapp.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WardrobeViewModel(private val wardrobeRepository: WardrobeRepository) : ViewModel() {

    val uiState: StateFlow<WardrobeUiState> =
        wardrobeRepository.getAllItems().map { items ->
            WardrobeUiState(items = items)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WardrobeUiState()
        )

    fun getItem(id: Long): Flow<ClothingItem?> {
        return wardrobeRepository.getItem(id)
    }

    fun addItem(item: ClothingItem) {
        viewModelScope.launch {
            wardrobeRepository.insertItem(item)
        }
    }

    fun deleteItem(item: ClothingItem) {
        viewModelScope.launch {
            wardrobeRepository.deleteItem(item)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return WardrobeViewModel(
                    (application as WardrobeApplication).container.wardrobeRepository
                ) as T
            }
        }
    }
}
