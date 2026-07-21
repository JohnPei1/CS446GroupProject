package com.example.wardrobeapp.domain.model

/**
 * Constraints for outfit generation (e.g., weather, occasion).
 */
data class OutfitConstraints(
    val weather: WeatherInfo? = null,
    val occasion: String? = null,
    val userPrompt: String? = null
)
