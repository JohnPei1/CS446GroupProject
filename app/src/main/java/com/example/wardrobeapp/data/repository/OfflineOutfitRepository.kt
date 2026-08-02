package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.ClothingItemDao
import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.dao.ScheduledOutfitDao
import com.example.wardrobeapp.data.local.entity.ClothingItemEntity
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity
import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.TimeZone

class OfflineOutfitRepository(
    private val outfitDao: OutfitDao,
    private val clothingItemDao: ClothingItemDao,
    private val scheduledOutfitDao: ScheduledOutfitDao
) : OutfitRepository {

    override fun getAllOutfits(): Flow<List<Outfit>> {
        // Combine both tables so saved outfits resolve their stored item ids to real clothing
        // items, and the list stays live when either outfits or wardrobe items change. Items
        // deleted from the wardrobe simply drop out of the outfit.
        return combine(
            outfitDao.getAllOutfits(),
            clothingItemDao.getAllItems()
        ) { outfits, items ->
            val itemsById = items.associateBy { it.id }
            outfits.map { it.toOutfit(itemsById) }
        }
    }

    override suspend fun saveOutfit(outfit: Outfit): Long =
        outfitDao.insert(outfit.toEntity())

    override suspend fun deleteOutfit(outfit: Outfit) {
        outfitDao.delete(outfit.toEntity())
        // Don't leave dangling plans pointing at a deleted outfit.
        scheduledOutfitDao.deleteByOutfitId(outfit.id)
    }

    override suspend fun scheduleOutfit(outfitId: Long, date: Long) {
        val day = normalizeDate(date)
        // One outfit per day: replace whatever was planned.
        scheduledOutfitDao.deleteByDate(day)
        scheduledOutfitDao.insert(ScheduledOutfitEntity(outfitId = outfitId, date = day))
    }

    override suspend fun unscheduleDate(date: Long) {
        scheduledOutfitDao.deleteByDate(normalizeDate(date))
    }

    override suspend fun getScheduledOutfit(date: Long): Outfit? {
        val scheduled = scheduledOutfitDao.getByDate(normalizeDate(date)) ?: return null
        val entity = outfitDao.getById(scheduled.outfitId) ?: return null
        val ids = entity.itemIds()
        val itemsById = clothingItemDao.getByIds(ids).associateBy { it.id }
        return entity.toOutfit(itemsById)
    }

    override fun observeScheduledOutfits(): Flow<Map<Long, Outfit>> {
        return combine(
            scheduledOutfitDao.getAllScheduledOutfits(),
            outfitDao.getAllOutfits(),
            clothingItemDao.getAllItems()
        ) { scheduled, outfits, items ->
            val outfitsById = outfits.associateBy { it.id }
            val itemsById = items.associateBy { it.id }
            scheduled.mapNotNull { plan ->
                outfitsById[plan.outfitId]?.let { entity -> plan.date to entity.toOutfit(itemsById) }
            }.toMap()
        }
    }

    private fun normalizeDate(timeInMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

// Extension functions for mapping

private fun OutfitEntity.itemIds(): List<Long> =
    clothingItemIds.split(",").mapNotNull { it.trim().toLongOrNull() }

private fun OutfitEntity.toOutfit(itemsById: Map<Long, ClothingItemEntity>): Outfit = Outfit(
    id = id,
    name = name,
    items = itemIds().mapNotNull { itemsById[it]?.toDomainModel() }
)

fun Outfit.toEntity(): OutfitEntity = OutfitEntity(
    id = id,
    name = name,
    clothingItemIds = items.joinToString(",") { it.id.toString() }
)
