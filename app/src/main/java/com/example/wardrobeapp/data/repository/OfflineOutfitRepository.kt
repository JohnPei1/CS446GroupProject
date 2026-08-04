package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.ClothingItemDao
import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.dao.ScheduledOutfitDao
import com.example.wardrobeapp.data.local.entity.ClothingItemEntity
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
        // If this is what's currently scheduled for today, deleting it should undo the "worn"
        // mark that scheduling it applied -- otherwise items stay stuck showing as worn with no
        // schedule left to explain it. Checked by identity (is this outfit today's plan?)
        // before the plan row itself gets removed below.
        val today = normalizeToUtcDay(System.currentTimeMillis())
        val todaysOutfit = getScheduledOutfit(today)
        if (todaysOutfit?.id == outfit.id && todaysOutfit.items.isNotEmpty()) {
            clothingItemDao.unmarkWorn(todaysOutfit.items.map { it.id })
        }
        outfitDao.delete(outfit.toEntity())
        // Don't leave dangling plans pointing at a deleted outfit.
        scheduledOutfitDao.deleteByOutfitId(outfit.id)
    }

    // date/day parameters below are always already-resolved day-keys by the time they reach the
    // repository (from a date picker, or a caller's own normalizeToUtcDay(System.currentTimeMillis())
    // call) -- floor them, don't re-run local-time-zone interpretation, or a value that's already
    // correct gets shifted a day backward. See domain.model.DateUtils for why.

    override suspend fun scheduleOutfit(outfitId: Long, date: Long) {
        val day = floorToUtcMidnight(date)
        // One outfit per day: replace whatever was planned.
        scheduledOutfitDao.deleteByDate(day)
        scheduledOutfitDao.insert(ScheduledOutfitEntity(outfitId = outfitId, date = day))
    }

    override suspend fun unscheduleDate(date: Long) {
        val day = floorToUtcMidnight(date)
        // Same reasoning as deleteOutfit: removing today's plan should undo the "worn" mark
        // scheduling it for today applied, not just erase the plan and leave items stuck
        // showing as worn with nothing left to explain it.
        if (day == normalizeToUtcDay(System.currentTimeMillis())) {
            val items = getScheduledOutfit(day)?.items.orEmpty()
            if (items.isNotEmpty()) clothingItemDao.unmarkWorn(items.map { it.id })
        }
        scheduledOutfitDao.deleteByDate(day)
    }

    override suspend fun getScheduledOutfit(date: Long): Outfit? {
        val scheduled = scheduledOutfitDao.getByDate(floorToUtcMidnight(date)) ?: return null
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
