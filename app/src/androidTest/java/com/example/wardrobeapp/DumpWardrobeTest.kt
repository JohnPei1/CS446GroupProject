package com.example.wardrobeapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.repository.OfflineWardrobeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only dump of the current wardrobe for planning demo scenarios. No writes at all. */
@RunWith(AndroidJUnit4::class)
class DumpWardrobeTest {

    @Test
    fun dumpWardrobe() {
    runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = OfflineWardrobeRepository(AppDatabase.getDatabase(context).clothingItemDao())
        val items = repository.getAllItems().first()
        Log.i(TAG, "TOTAL=${items.size}")
        items.sortedBy { it.category }.forEach { item ->
            Log.i(
                TAG,
                "id=${item.id} cat=${item.category} name=${item.name} brand=${item.brand} " +
                    "color=${item.color} colorFamily=${item.colorFamily} tags=${item.tags} " +
                    "warmth=${item.warmthLevel} waterResistant=${item.isWaterResistant} " +
                    "hasPhoto=${item.imagePath.isNotBlank()} notes=${item.description}"
            )
        }
    }
    }

    companion object {
        private const val TAG = "WardrobeDump"
    }
}
