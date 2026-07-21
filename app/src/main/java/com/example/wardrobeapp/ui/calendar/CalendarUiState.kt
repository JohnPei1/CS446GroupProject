package com.example.wardrobeapp.ui.calendar

import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.WeatherInfo

data class CalendarUiState(
    val scheduledOutfits: Map<Long, Outfit> = emptyMap(),
    val weatherForecast: Map<Long, WeatherInfo> = emptyMap(),
    val selectedDate: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false
)
