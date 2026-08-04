package com.example.wardrobeapp.ui.outfit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyOutfitsUiState(
    val todayOutfit: Outfit? = null,
    val savedOutfits: List<Outfit> = emptyList(),
    val isLoading: Boolean = true,
    /** Set when scheduling needs the user to confirm replacing an existing day plan. */
    val pendingSchedule: PendingSchedule? = null,
    /** One-shot feedback message to show in a snackbar; cleared via consumeMessage(). */
    val userMessage: String? = null
)

/**
 * Backs the My Outfits home tab: today's planned outfit plus the saved outfit library, both
 * kept live from the database so plans made anywhere (generator, calendar, here) show up
 * immediately.
 */
class MyOutfitsViewModel(
    private val outfitRepository: OutfitRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyOutfitsUiState())
    val uiState: StateFlow<MyOutfitsUiState> = _uiState.asStateFlow()

    /** The outfit a schedule confirmation refers to, kept alongside [MyOutfitsUiState.pendingSchedule]. */
    private var pendingOutfit: Outfit? = null

    init {
        viewModelScope.launch {
            combine(
                outfitRepository.getAllOutfits(),
                outfitRepository.observeScheduledOutfits()
            ) { outfits, schedule ->
                outfits to schedule[normalizeToUtcDay(System.currentTimeMillis())]
            }.collect { (outfits, todayOutfit) ->
                _uiState.update {
                    it.copy(savedOutfits = outfits, todayOutfit = todayOutfit, isLoading = false)
                }
            }
        }
    }

    /**
     * Plans [outfit] for [date]. If that day already has a different outfit, the request is
     * parked in [MyOutfitsUiState.pendingSchedule] for the user to confirm the replacement.
     */
    /** [date] must already be a resolved day-key -- this only floors it. */
    fun requestWear(outfit: Outfit, date: Long) {
        viewModelScope.launch {
            val day = floorToUtcMidnight(date)
            val existing = runCatching { outfitRepository.getScheduledOutfit(day) }.getOrNull()
            if (existing != null && existing.id != outfit.id) {
                pendingOutfit = outfit
                _uiState.update { it.copy(pendingSchedule = PendingSchedule(day, existing.name)) }
            } else {
                performSchedule(outfit, day)
            }
        }
    }

    fun confirmPendingSchedule() {
        val pending = _uiState.value.pendingSchedule ?: return
        val outfit = pendingOutfit ?: return
        pendingOutfit = null
        _uiState.update { it.copy(pendingSchedule = null) }
        viewModelScope.launch { performSchedule(outfit, pending.date) }
    }

    fun dismissPendingSchedule() {
        pendingOutfit = null
        _uiState.update { it.copy(pendingSchedule = null) }
    }

    private suspend fun performSchedule(outfit: Outfit, day: Long) {
        runCatching {
            outfitRepository.scheduleOutfit(outfit.id, day)
            // Wearing it today also records the items as worn (feeds anti-repetition scoring).
            val isToday = day == normalizeToUtcDay(System.currentTimeMillis())
            if (isToday) wardrobeRepository.markItemsWorn(outfit.items.map { it.id })
            isToday
        }.onSuccess { isToday ->
            _uiState.update {
                it.copy(
                    userMessage = if (isToday) {
                        "\"${outfit.name}\" planned for today — items marked as worn."
                    } else {
                        "\"${outfit.name}\" planned for ${planDateLabel(day)}."
                    }
                )
            }
        }.onFailure {
            _uiState.update { it.copy(userMessage = "Couldn't plan the outfit.") }
        }
    }

    fun removeTodayPlan() {
        viewModelScope.launch {
            runCatching { outfitRepository.unscheduleDate(normalizeToUtcDay(System.currentTimeMillis())) }
                .onSuccess { _uiState.update { it.copy(userMessage = "Removed today's plan.") } }
                .onFailure { _uiState.update { it.copy(userMessage = "Couldn't remove today's plan.") } }
        }
    }

    fun delete(outfit: Outfit) {
        viewModelScope.launch {
            runCatching { outfitRepository.deleteOutfit(outfit) }
                .onFailure { _uiState.update { it.copy(userMessage = "Couldn't delete the outfit.") } }
        }
    }

    /** Clears the one-shot snackbar message after the UI has shown it. */
    fun consumeMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return MyOutfitsViewModel(
                        outfitRepository = container.outfitRepository,
                        wardrobeRepository = container.wardrobeRepository
                    ) as T
                }
            }
    }
}
