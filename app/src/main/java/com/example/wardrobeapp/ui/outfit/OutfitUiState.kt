package com.example.wardrobeapp.ui.outfit

import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.WeatherInfo

data class OutfitUiState(
    val generatedOutfit: Outfit? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /**
     * Set when the wardrobe can't satisfy the request (no swimwear for Swim, nothing warm
     * enough for the forecast) -- shown as a "add items to your wardrobe" state, not an error.
     */
    val wardrobeGap: String? = null,
    val lastWeatherUsed: WeatherInfo? = null,
    /** True when the last generation wanted weather but none was available for that day. */
    val weatherUnavailable: Boolean = false,
    /** True once the current outfit has been saved (resets on each generation). */
    val saved: Boolean = false,
    /** The day (UTC midnight) the current outfit was planned for, once scheduled. */
    val scheduledFor: Long? = null,
    /** Set when scheduling needs the user to confirm replacing an existing day plan. */
    val pendingSchedule: PendingSchedule? = null,
    /**
     * True when saving (without scheduling) needs a name from the user first -- a generated
     * outfit only has a generic strategy name ("AI Pick", "Everyday Outfit") until it's either
     * scheduled (named after that date) or saved on its own (named by the user here).
     */
    val promptForSaveName: Boolean = false,
    /** One-shot feedback message to show in a snackbar; cleared via consumeMessage(). */
    val userMessage: String? = null
)
