package com.example.wardrobeapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the OpenMeteo API.
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") current: Boolean = true
    ): WeatherDto
}
