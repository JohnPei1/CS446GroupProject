package com.example.wardrobeapp.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState(selectedDate = DateUtils.getTodayUtcMidnight()))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var outfitJob: Job? = null

    init {
        loadCalendarData()
    }

    private fun loadCalendarData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Fetch weather forecast from repository
            val weather = weatherRepository.getForecastOneWeek(DEFAULT_LAT, DEFAULT_LON)
            _uiState.update { it.copy(weatherForecast = weather, isLoading = false) }
            
            // Start collecting outfit for the initial selected date
            observeOutfitForSelectedDate(DateUtils.getTodayUtcMidnight())
        }
    }

    fun onDateSelected(date: Long) {
        val normalizedDate = DateUtils.normalizeDate(date)
        _uiState.update { it.copy(selectedDate = normalizedDate) }
        observeOutfitForSelectedDate(normalizedDate)
    }

    private fun observeOutfitForSelectedDate(date: Long) {
        outfitJob?.cancel()
        outfitJob = viewModelScope.launch {
            outfitRepository.getScheduledOutfit(date).collect { outfit ->
                _uiState.update { state ->
                    val newOutfits = state.scheduledOutfits.toMutableMap()
                    if (outfit != null) {
                        newOutfits[date] = outfit
                    } else {
                        newOutfits.remove(date)
                    }
                    state.copy(scheduledOutfits = newOutfits)
                }
            }
        }
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
