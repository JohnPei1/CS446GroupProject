package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.ClothingItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for wardrobe data.
 */
interface WardrobeRepository {
    fun getAllItems(): Flow<List<ClothingItem>>
    fun getItem(id: Long): Flow<ClothingItem?>
    suspend fun insertItem(item: ClothingItem)
    suspend fun deleteItem(item: ClothingItem)
    suspend fun markItemsWorn(ids: List<Long>)

    /** One-time cleanup: zeroes timesWorn/lastWornDate on every item. Nothing else is touched. */
    suspend fun resetWornStats()
}
