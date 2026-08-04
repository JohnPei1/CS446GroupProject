package com.example.wardrobeapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.wardrobeapp.dataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed rolling history, capped at [ROLLING_WINDOW] distinct ids per category. Stored
 * as a comma-separated string per category in the same "settings" DataStore file the rest of the
 * app's lightweight preferences already use -- nothing here touches the Room wardrobe database.
 */
class OfflineRecentPicksRepository(private val context: Context) : RecentPicksRepository {

    override suspend fun getRecentIds(category: String): List<Long> {
        val raw = context.dataStore.data.first()[keyFor(category)].orEmpty()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    override suspend fun recordPicks(category: String, itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val key = keyFor(category)
            val existing = prefs[key].orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
            // Newest last; re-showing an id moves it to the back instead of counting twice
            // against the window, and only the most recent ROLLING_WINDOW ids are kept.
            val updated = (existing - itemIds.toSet()) + itemIds
            prefs[key] = updated.takeLast(ROLLING_WINDOW).joinToString(",")
        }
    }

    private fun keyFor(category: String) = stringPreferencesKey("recent_picks_${category.lowercase()}")

    companion object {
        private const val ROLLING_WINDOW = 5
    }
}
