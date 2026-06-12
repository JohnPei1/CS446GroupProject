package com.example.wardrobeapp.ui.calendar

import com.example.wardrobeapp.domain.model.Outfit

data class CalendarUiState(
    val scheduledOutfits: Map<Long, Outfit> = emptyMap(),
    val isLoading: Boolean = false
)
