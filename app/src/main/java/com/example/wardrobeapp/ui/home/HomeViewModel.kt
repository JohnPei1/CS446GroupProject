package com.example.wardrobeapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val weatherRepository: WeatherRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadWeather()
    }

    private fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // For now, using mock coordinates
                val weather = weatherRepository.getCurrentWeather(43.46, -80.52)
                _uiState.value = HomeUiState(weather = weather, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = HomeUiState(error = e.message, isLoading = false)
            }
        }
    }
}
