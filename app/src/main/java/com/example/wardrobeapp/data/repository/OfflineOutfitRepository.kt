package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineOutfitRepository(private val outfitDao: OutfitDao) : OutfitRepository {
    override fun getAllOutfits(): Flow<List<Outfit>> {
        // Simple mapping for the prototype. In a real app, we'd fetch items from ClothingItemDao
        return outfitDao.getAllOutfits().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveOutfit(outfit: Outfit) {
        outfitDao.insert(outfit.toEntity())
    }

    override suspend fun scheduleOutfit(outfitId: Long, date: Long) {
        // Implementation for scheduling will go here (John's task)
    }
}

// Extension functions for mapping
fun OutfitEntity.toDomainModel(): Outfit = Outfit(
    id = id,
    name = name,
    items = emptyList() // Simplified for prototype
)

fun Outfit.toEntity(): OutfitEntity = OutfitEntity(
    id = id,
    name = name,
    clothingItemIds = items.joinToString(",") { it.id.toString() }
)
