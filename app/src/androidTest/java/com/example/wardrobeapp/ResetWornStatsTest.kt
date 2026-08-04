package com.example.wardrobeapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.repository.OfflineWardrobeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One-time cleanup for timesWorn/lastWornDate left stale by schedule/unschedule cycles that
 * predate the unmarkWorn fix. Runs against the real, live wardrobe_database (same connection the
 * app itself uses) so it's the actual Room code path, not manual file surgery. Only ever touches
 * those two columns -- logs the item count before and after so a passing run is real proof
 * nothing was inserted, deleted, or otherwise altered.
 */
@RunWith(AndroidJUnit4::class)
class ResetWornStatsTest {

    @Test
    fun resetWornStats_zeroesWearTrackingWithoutTouchingItemsOrCount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = OfflineWardrobeRepository(AppDatabase.getDatabase(context).clothingItemDao())

        val before = repository.getAllItems().first()
        Log.i(
            TAG,
            "BEFORE count=${before.size} withWearData=${before.count { it.timesWorn != 0 || it.lastWornDate != null }}"
        )

        repository.resetWornStats()

        val after = repository.getAllItems().first()
        Log.i(TAG, "AFTER count=${after.size} withWearData=${after.count { it.timesWorn != 0 || it.lastWornDate != null }}")

        assertEquals("Item count must be unchanged -- this only resets wear stats", before.size, after.size)
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertTrue(after.all { it.timesWorn == 0 && it.lastWornDate == null })
        // Everything else on every item must be untouched.
        val beforeById = before.associateBy { it.id }
        after.forEach { item ->
            val original = beforeById.getValue(item.id)
            assertEquals(original.copy(timesWorn = 0, lastWornDate = null), item)
        }
    }

    companion object {
        private const val TAG = "ResetWornStats"
    }
}
