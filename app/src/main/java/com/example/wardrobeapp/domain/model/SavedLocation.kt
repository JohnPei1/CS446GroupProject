package com.example.wardrobeapp.domain.model

/**
 * A user-chosen weather location, resolved from a city search via geocoding and persisted in
 * settings. When none is saved, weather falls back to the app's default coordinates.
 */
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)
