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

    // Geocoding lives on a different Open-Meteo host; an absolute URL here overrides the base URL.
    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchLocation(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingDto
}
