package com.example.wardrobeapp.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Data transfer objects for the Open-Meteo geocoding API (city name -> coordinates).
 * `results` is absent (null) when nothing matches the query.
 */
data class GeocodingDto(
    val results: List<GeocodingResultDto>?
)

data class GeocodingResultDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    @SerializedName("admin1")
    val admin1: String? // state/province, e.g. "Ontario"
)
