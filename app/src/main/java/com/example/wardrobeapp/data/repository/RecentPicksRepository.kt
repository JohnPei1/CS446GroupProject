package com.example.wardrobeapp.data.repository

/**
 * Tracks a rolling history of recently-picked item ids per category, so outfit generation can
 * favor variety -- independent of whether an outfit is ever saved or scheduled.
 */
interface RecentPicksRepository {
    /** Up to the last few distinct item ids shown for [category], oldest first. */
    suspend fun getRecentIds(category: String): List<Long>

    /** Records that [itemIds] were just shown for [category], rolling the window forward. */
    suspend fun recordPicks(category: String, itemIds: List<Long>)
}
