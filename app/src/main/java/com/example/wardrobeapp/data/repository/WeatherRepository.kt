package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.SavedLocation
import com.example.wardrobeapp.domain.model.WeatherInfo
import com.example.wardrobeapp.data.remote.RetrofitInstance.apiService
import com.example.wardrobeapp.data.remote.WeatherDto
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import java.util.Calendar
import java.util.TimeZone

/**
 * Repository for fetching weather data.
 */
class WeatherRepository {

    /**
     * Resolves a typed city to coordinates; null when nothing matches. Supports an optional
     * region after a comma ("Waterloo, Ontario") since the geocoding API only matches on the
     * city name. Exact name matches are preferred over the API's population-ranked fuzzy
     * matches -- Open-Meteo also matches historical names, so a plain "Waterloo" would
     * otherwise resolve to Austin, Texas (its original settlement name).
     */
    suspend fun geocodeCity(query: String): SavedLocation? {
        val parts = query.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val city = parts.firstOrNull() ?: return null
        val regionHints = parts.drop(1)

        val results = apiService.searchLocation(city, count = 10).results.orEmpty()
        if (results.isEmpty()) return null

        val exactNames = results.filter { it.name.equals(city, ignoreCase = true) }
        val pool = exactNames.ifEmpty { results }
        val best = pool.firstOrNull { candidate ->
            regionHints.isNotEmpty() && regionHints.all { hint ->
                candidate.admin1?.contains(hint, ignoreCase = true) == true ||
                    candidate.country?.contains(hint, ignoreCase = true) == true
            }
        } ?: pool.first()

        val label = listOfNotNull(best.name, best.admin1, best.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return SavedLocation(name = label, latitude = best.latitude, longitude = best.longitude)
    }

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo {
        val response : WeatherDto = apiService.getWeather(lat, lon);
        val weatherInfo = WeatherInfo(
            temperature = response.current.temperature,
            condition = fromWmoCode(response.current.weatherCode)
        );
        return weatherInfo;
    }
    suspend fun getForecastOneWeek(lat: Double, lon: Double): Map<Long, WeatherInfo> {
        val response: WeatherDto = apiService.getWeather(lat, lon);
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val weatherMap = mutableMapOf<Long, WeatherInfo>()
        val today = normalizeToUtcDay(System.currentTimeMillis())

        weatherMap[today] = WeatherInfo(
            temperature = response.daily.temperatureMax[0],
            condition = fromWmoCode(response.daily.weatherCode[0])
        )
        calendar.setTimeInMillis(today)
        for (i in 1..6){
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val futureDate = floorToUtcMidnight(calendar.timeInMillis)
            weatherMap[futureDate] = WeatherInfo(
                temperature = response.daily.temperatureMax[i],
                condition = fromWmoCode(response.daily.weatherCode[i])
            )
        }
        return weatherMap
    }

    private fun fromWmoCode(code: Int): String {
        return when (code) {
            0 -> "Sunny"
            1 -> "Mainly Sunny"
            2, 3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, -> "Rain"
            80, 81, 82 -> "Showers"
            66, 67 -> "Freezing Rain"
            71, 73, 75, 77, 85, 86 -> "Snow"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}
