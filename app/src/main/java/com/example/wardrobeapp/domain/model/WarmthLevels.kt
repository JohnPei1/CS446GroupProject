package com.example.wardrobeapp.domain.model

/**
 * Single source of truth for how the 1-5 warmth level maps to outdoor temperature. The item form
 * shows these ranges to the user, and outfit scoring uses [idealFor] to pick a target level for
 * the forecast -- so what the user is told always matches what the generator actually does.
 */
object WarmthLevels {

    /** Target warmth level for a given temperature. */
    fun idealFor(temperatureCelsius: Double): Int = when {
        temperatureCelsius <= 0 -> 5
        temperatureCelsius <= 8 -> 4
        temperatureCelsius <= 15 -> 3
        temperatureCelsius <= 22 -> 2
        else -> 1
    }

    /** Human-readable temperature range a warmth level is best suited for. */
    fun temperatureRangeLabel(level: Int): String = when (level.coerceIn(1, 5)) {
        1 -> "23°C and warmer"
        2 -> "16–22°C"
        3 -> "9–15°C"
        4 -> "1–8°C"
        else -> "0°C and colder"
    }
}
