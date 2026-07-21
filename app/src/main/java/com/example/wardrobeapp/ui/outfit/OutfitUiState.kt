package com.example.wardrobeapp.ui.outfit

import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.WeatherInfo

data class OutfitUiState(
    val generatedOutfit: Outfit? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when [generatedOutfit] came from a saved/scheduled plan for the day, not a fresh generation. */
    val isPlanned: Boolean = false,
    val lastWeatherUsed: WeatherInfo? = null
)
