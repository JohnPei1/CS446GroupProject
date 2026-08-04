package com.example.wardrobeapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.repository.OfflineWardrobeRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.strategy.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One-time restore of the 13 items recoverable from old AI-generation logcat output, after an
 * unintended uninstall wiped the wardrobe. Only color/colorFamily/tags/warmthLevel/
 * isWaterResistant/notes survived (that's all that's ever sent to the AI/logged) -- name, brand,
 * and photo never leave the device and weren't recoverable, so every item here gets a placeholder
 * name (its recovered color) and a note flagging it as incomplete. No Tops/Bottoms were
 * recoverable at all. Runs via plain am instrument (see the accompanying adb command), NOT
 * `connectedDebugAndroidTest` -- that Gradle task is what correlated with every prior data loss
 * this session, so it must never be invoked against this device again.
 */
@RunWith(AndroidJUnit4::class)
class InsertRecoveredItemsTest {

    @Test
    fun restoreRecoveredItems() {
    runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = OfflineWardrobeRepository(AppDatabase.getDatabase(context).clothingItemDao())

        val flag = "[Recovered from AI logs -- add a real photo and name]"
        fun item(
            category: String,
            color: String,
            colorFamily: String,
            tags: List<String>,
            warmthLevel: Int,
            isWaterResistant: Boolean,
            notes: String? = null
        ) = ClothingItem(
            name = color,
            category = category,
            imagePath = "",
            color = color,
            colorFamily = colorFamily,
            tags = tags,
            warmthLevel = warmthLevel,
            isWaterResistant = isWaterResistant,
            description = if (notes != null) "$notes\n\n$flag" else flag
        )

        val recovered = listOf(
            item(Category.ACCESSORIES, "Blue face and brown leather straps", "Cool", listOf("Business Casual", "Formal", "Casual", "Party"), 1, false),
            item(Category.ACCESSORIES, "Blue face and silver links", "Cool", listOf("Business Casual", "Formal", "Party"), 1, false),
            item(Category.ACCESSORIES, "Black with diagonal white stripes and uw emblem pattern", "Neutral", listOf("Formal"), 1, false),
            item(Category.ACCESSORIES, "Silver", "Neutral", listOf("Casual", "Workout", "Loungewear", "Outdoor", "Party"), 1, false),
            item(Category.FOOTWEAR, "Black", "Neutral", listOf("Outdoor"), 5, false, "Good for snow"),
            item(Category.FOOTWEAR, "Black", "Neutral", listOf("Casual", "Business Casual", "Workout", "Loungewear", "Outdoor", "Party"), 4, false),
            item(Category.FOOTWEAR, "Brown", "Neutral", listOf("Formal"), 3, false, "Formal"),
            item(Category.FOOTWEAR, "White with bright orange accents", "Bright", listOf("Casual", "Workout", "Party", "Outdoor", "Loungewear"), 3, false),
            item(Category.OUTERWEAR, "Dark blue", "Neutral", listOf("Formal"), 5, false, "very formal"),
            item(Category.OUTERWEAR, "Bright red", "Bright", listOf("Casual", "Outdoor", "Party"), 4, false),
            item(Category.OUTERWEAR, "Light grey", "Neutral", listOf("Casual", "Outdoor", "Party", "Loungewear", "Workout"), 4, false),
            item(Category.OUTERWEAR, "Dark grey", "Neutral", listOf("Outdoor", "Loungewear", "Casual"), 5, false),
            item(Category.OUTERWEAR, "Black", "Neutral", listOf("Outdoor"), 5, false)
        )

        val before = repository.getAllItems().first()
        Log.i(TAG, "BEFORE count=${before.size}")

        recovered.forEach { repository.insertItem(it) }

        val after = repository.getAllItems().first()
        Log.i(TAG, "AFTER count=${after.size} (inserted ${recovered.size})")
    }
    }

    companion object {
        private const val TAG = "InsertRecovered"
    }
}
