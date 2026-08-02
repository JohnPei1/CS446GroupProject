package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.ClothingItemDao
import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.dao.ScheduledOutfitDao
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class OfflineOutfitRepository(
    private val outfitDao: OutfitDao,
    private val scheduledOutfitDao: ScheduledOutfitDao,
    private val clothingItemDao: ClothingItemDao
) : OutfitRepository {
    override fun getAllOutfits(): Flow<List<Outfit>> {
        return outfitDao.getAllOutfits().map { entities ->
            entities.map { it.toDomainModel(emptyList()) }
        }
    }

    override suspend fun saveOutfit(outfit: Outfit): Long {
        return outfitDao.insert(outfit.toEntity())
    }

    override suspend fun scheduleOutfit(outfitId: Long, date: Long) {
        val normalizedDate = DateUtils.normalizeDate(date)
        // First delete any existing schedule for this date to ensure override
        scheduledOutfitDao.deleteByDate(normalizedDate)
        scheduledOutfitDao.insert(
            ScheduledOutfitEntity(
                outfitId = outfitId,
                date = normalizedDate
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getScheduledOutfit(date: Long): Flow<Outfit?> {
        val normalizedDate = DateUtils.normalizeDate(date)
        return scheduledOutfitDao.getScheduledOutfitByDateFlow(normalizedDate).flatMapLatest { scheduled ->
            flow {
                if (scheduled != null) {
                    val entity = outfitDao.getOutfitById(scheduled.outfitId)
                    if (entity != null) {
                        val itemIds = entity.clothingItemIds.split(",")
                            .filter { it.isNotBlank() }
                            .map { it.toLong() }
                        val itemEntities = clothingItemDao.getItemsByIds(itemIds)
                        emit(entity.toDomainModel(itemEntities.map { it.toDomainModel() }))
                    } else {
                        emit(null)
                    }
                } else {
                    emit(null)
                }
            }
        }
    }
}

// Extension functions for mapping
fun OutfitEntity.toDomainModel(items: List<com.example.wardrobeapp.domain.model.ClothingItem>): Outfit = Outfit(
    id = id,
    name = name,
    items = items
)

fun Outfit.toEntity(): OutfitEntity = OutfitEntity(
    id = if (id == 0L) 0 else id, // Let Room auto-generate if 0
    name = name,
    clothingItemIds = items.joinToString(",") { it.id.toString() }
)
