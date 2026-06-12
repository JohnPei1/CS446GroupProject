package com.example.wardrobeapp.ui.home

import com.example.wardrobeapp.domain.model.WeatherInfo

data class HomeUiState(
    val weather: WeatherInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
