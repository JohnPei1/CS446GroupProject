package com.example.wardrobeapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the OpenMeteo API.
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String="temperature_2m_max,temperature_2m_min,weather_code",
        @Query("current") current: String="temperature_2m,weather_code"
    ): WeatherDto
}
