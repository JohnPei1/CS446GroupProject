package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.Flow

/**
 * Repository for outfit and scheduling data. Scheduling is one-outfit-per-day: planning an
 * outfit for a date replaces whatever was planned before (callers confirm with the user first).
 */
interface OutfitRepository {
    fun getAllOutfits(): Flow<List<Outfit>>

    /** Saves (or updates, when [Outfit.id] is non-zero) and returns the outfit's id. */
    suspend fun saveOutfit(outfit: Outfit): Long

    suspend fun deleteOutfit(outfit: Outfit)

    /** Plans a saved outfit for a day, replacing any existing plan for that day. */
    suspend fun scheduleOutfit(outfitId: Long, date: Long)

    /** Removes the plan for a day, if any. */
    suspend fun unscheduleDate(date: Long)

    suspend fun getScheduledOutfit(date: Long): Outfit?

    /** Live map of day (UTC midnight) -> planned outfit, resolved to real wardrobe items. */
    fun observeScheduledOutfits(): Flow<Map<Long, Outfit>>
}
