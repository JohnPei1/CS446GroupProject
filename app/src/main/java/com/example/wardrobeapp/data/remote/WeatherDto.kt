package com.example.wardrobeapp.data.remote

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Data transfer object for weather data.
 */
@Serializable
data class WeatherDto(
    val daily: DailyWeatherDto,
    val current: CurrentWeatherDto
)

@Serializable
data class DailyWeatherDto(
    @SerializedName("temperature_2m_max")
    val temperatureMax: Array <Double>,
    @SerializedName("weather_code")
    val weatherCode: Array<Int>
)

@Serializable
data class CurrentWeatherDto (
    @SerializedName("temperature_2m")
    val temperature: Double,
    @SerializedName("weather_code")
    val weatherCode: Int
)

