package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.Flow

/**
 * Repository for outfit and scheduling data.
 */
interface OutfitRepository {
    fun getAllOutfits(): Flow<List<Outfit>>
    suspend fun saveOutfit(outfit: Outfit): Long
    suspend fun scheduleOutfit(outfitId: Long, date: Long)
    fun getScheduledOutfit(date: Long): Flow<Outfit?>
}
