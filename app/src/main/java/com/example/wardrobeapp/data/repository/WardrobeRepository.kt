package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.ClothingItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for wardrobe data.
 */
interface WardrobeRepository {
    fun getAllItems(): Flow<List<ClothingItem>>
    suspend fun insertItem(item: ClothingItem)
    suspend fun deleteItem(item: ClothingItem)
}
