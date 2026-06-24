package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.WeatherInfo

class OfflineWeatherRepository : WeatherRepository {
    override suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo {
        // Return a mock weather info for the prototype
        return WeatherInfo(
            temperature = 8.0, // Matching teammate's current hardcoded value
            condition = "Cloudy"
        )
    }
}
