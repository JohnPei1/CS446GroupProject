package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.WeatherInfo
import java.util.Calendar
import java.util.TimeZone

class OfflineWeatherRepository : WeatherRepository {
    override suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo {
        // Return a mock weather info for the prototype
        return WeatherInfo(
            temperature = 8.0, // Matching teammate's current hardcoded value
            condition = "Cloudy"
        )
    }

    override suspend fun getForecastOneWeek(lat: Double, lon: Double): Map<Long, WeatherInfo> {
        val weather = mutableMapOf<Long, WeatherInfo>()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        
        // Current day
        weather[normalizeDate(System.currentTimeMillis())] = WeatherInfo(22.0, "Sunny")
        
        // Next 7 days
        calendar.setTimeInMillis(normalizeDate(System.currentTimeMillis()))
        for (i in 1..7) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val futureDate = normalizeDate(calendar.timeInMillis)
            weather[futureDate] = WeatherInfo(20.0 + i, if (i % 2 == 0) "Cloudy" else "Sunny")
        }
        return weather
    }

    private fun normalizeDate(timeInMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
