package com.example.wardrobeapp.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class CalendarViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadCalendarData()
    }

    private fun loadCalendarData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Fetch weather forecast from repository
            val weather = weatherRepository.getForecastOneWeek(DEFAULT_LAT, DEFAULT_LON)

            // Fetch scheduled outfits for selected date (initially today)
            val today = normalizeDate(System.currentTimeMillis())
            val todayOutfit = outfitRepository.getScheduledOutfit(today)
            
            val outfits = mutableMapOf<Long, Outfit>()
            todayOutfit?.let { outfits[today] = it }

            _uiState.update {
                it.copy(
                    scheduledOutfits = outfits,
                    weatherForecast = weather,
                    isLoading = false
                )
            }
        }
    }

    fun onDateSelected(date: Long) {
        val normalizedDate = normalizeDate(date)
        _uiState.update { it.copy(selectedDate = normalizedDate, isLoading = true) }
        
        viewModelScope.launch {
            val outfit = outfitRepository.getScheduledOutfit(normalizedDate)
            _uiState.update { state ->
                val newOutfits = state.scheduledOutfits.toMutableMap()
                if (outfit != null) {
                    newOutfits[normalizedDate] = outfit
                }
                state.copy(
                    scheduledOutfits = newOutfits,
                    isLoading = false
                )
            }
        }
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
                        weatherRepository = container.weatherRepository
                    ) as T
                }
            }
    }
}
