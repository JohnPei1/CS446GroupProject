package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.WeatherInfo

/**
 * Repository for fetching weather data.
 */
interface WeatherRepository {
    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo
    suspend fun getForecastOneWeek(lat: Double, lon: Double): Map<Long, WeatherInfo>
}
