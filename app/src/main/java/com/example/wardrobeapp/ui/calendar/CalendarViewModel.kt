package com.example.wardrobeapp.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.SettingsRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class CalendarViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CalendarUiState(selectedDate = normalizeDate(System.currentTimeMillis()))
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadForecast()
        // Live map of day -> planned outfit, so plans made anywhere in the app (generator,
        // My Outfits, the outfit picker) show up on the calendar immediately.
        viewModelScope.launch {
            outfitRepository.observeScheduledOutfits().collect { scheduled ->
                _uiState.update { it.copy(scheduledOutfits = scheduled) }
            }
        }
    }

    private fun loadForecast() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Forecast for the saved settings location (falling back to the app default);
            // a failed fetch shows an empty forecast instead of crashing the screen.
            val location = settingsRepository.savedLocation.first()
            val weather = runCatching {
                weatherRepository.getForecastOneWeek(
                    location?.latitude ?: DEFAULT_LAT,
                    location?.longitude ?: DEFAULT_LON
                )
            }.getOrElse { emptyMap() }

            _uiState.update {
                it.copy(weatherForecast = weather, isLoading = false)
            }
        }
    }

    fun onDateSelected(date: Long) {
        _uiState.update { it.copy(selectedDate = normalizeDate(date)) }
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

    companion object {
        private const val DEFAULT_LAT = 43.46
        private const val DEFAULT_LON = -80.52

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return CalendarViewModel(
                        outfitRepository = container.outfitRepository,
                        weatherRepository = container.weatherRepository,
                        settingsRepository = container.settingsRepository
                    ) as T
                }
            }
    }
}
