package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.ClothingItemDao
import com.example.wardrobeapp.data.local.entity.ClothingItemEntity
import com.example.wardrobeapp.domain.model.ClothingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineWardrobeRepository(private val clothingItemDao: ClothingItemDao) : WardrobeRepository {
    override fun getAllItems(): Flow<List<ClothingItem>> {
        return clothingItemDao.getAllItems().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getItem(id: Long): Flow<ClothingItem?> {
        return clothingItemDao.getItem(id).map { it?.toDomainModel() }
    }

    override suspend fun insertItem(item: ClothingItem) {
        clothingItemDao.insert(item.toEntity())
    }

    override suspend fun deleteItem(item: ClothingItem) {
        clothingItemDao.delete(item.toEntity())
    }

    override suspend fun markItemsWorn(ids: List<Long>) {
        clothingItemDao.markWorn(ids, System.currentTimeMillis())
    }

    override suspend fun resetWornStats() {
        clothingItemDao.resetWornStats()
    }
}

// Mapping extensions
fun ClothingItemEntity.toDomainModel() = ClothingItem(
    id = id,
    name = name,
    category = category,
    imagePath = imagePath,
    brand = brand,
    color = color,
    description = description,
    tags = tags,
    season = season,
    colorFamily = colorFamily,
    warmthLevel = warmthLevel,
    isWaterResistant = isWaterResistant,
    isFavorite = isFavorite,
    timesWorn = timesWorn,
    lastWornDate = lastWornDate,
    dateAdded = dateAdded
)

fun ClothingItem.toEntity() = ClothingItemEntity(
    id = id,
    name = name,
    category = category,
    imagePath = imagePath,
    brand = brand,
    color = color,
    description = description,
    tags = tags,
    season = season,
    colorFamily = colorFamily,
    warmthLevel = warmthLevel,
    isWaterResistant = isWaterResistant,
    isFavorite = isFavorite,
    timesWorn = timesWorn,
    lastWornDate = lastWornDate,
    dateAdded = dateAdded
)
