package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone

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

    override suspend fun getScheduledOutfit(date: Long): Outfit? {
        // Mocking for prototype: return an outfit if today or yesterday
        val normalizedDate = normalizeDate(date)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val today = normalizeDate(System.currentTimeMillis())
        
        calendar.setTimeInMillis(today)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = normalizeDate(calendar.timeInMillis)

        return when (normalizedDate) {
            today -> Outfit(1, "Today's Outfit", listOf(
                ClothingItem(1, "Blue T-Shirt", "Tops", ""),
                ClothingItem(2, "Jeans", "Bottoms", "")
            ))
            yesterday -> Outfit(2, "Yesterday's Outfit", listOf(
                ClothingItem(3, "White Shirt", "Tops", ""),
                ClothingItem(4, "Chinos", "Bottoms", "")
            ))
            else -> null
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
