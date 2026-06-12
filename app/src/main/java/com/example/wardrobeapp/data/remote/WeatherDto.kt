package com.example.wardrobeapp.data.remote

import kotlinx.serialization.Serializable

/**
 * Data transfer object for weather data.
 */
@Serializable
data class WeatherDto(
    val current_weather: CurrentWeatherDto
)

@Serializable
data class CurrentWeatherDto(
    val temperature: Double,
    val weathercode: Int
)
