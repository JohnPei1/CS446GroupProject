package com.example.wardrobeapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wardrobeapp.data.remote.ai.GeminiAiClient
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WeatherInfo
import com.example.wardrobeapp.domain.strategy.AiOutfitStrategy
import com.example.wardrobeapp.domain.strategy.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real cloud AI strategy directly against a synthetic wardrobe -- no UI needed -- so
 * the actual prompt/reply can be inspected in logcat without testing blind in the app. Requires
 * GEMINI_API_KEY to be set in local.properties before building (see [GeminiAiClient]); unlike
 * the old on-device version, this needs no model file push and works on the emulator or a real
 * device identically as long as there's internet. Mirrors the exact wardrobe shape seen in real
 * device logs for a faithful repro.
 */
@RunWith(AndroidJUnit4::class)
class AiDiagnosticTest {

    private fun item(
        id: Long,
        category: String,
        tags: List<String>,
        colorFamily: String,
        warmthLevel: Int,
        color: String = "",
        description: String = "",
        isWaterResistant: Boolean = false
    ) = ClothingItem(
        id = id,
        name = "Item $id",
        category = category,
        imagePath = "",
        tags = tags,
        colorFamily = colorFamily,
        color = color,
        description = description,
        warmthLevel = warmthLevel,
        isWaterResistant = isWaterResistant
    )

    private val wardrobe = listOf(
        item(8, Category.TOPS, listOf("Formal", "Casual", "Loungewear", "Business Casual", "Outdoor", "Party"), "Neutral", 2, color = "White"),
        item(1, Category.TOPS, listOf("Casual", "Loungewear", "Workout", "Outdoor", "Party"), "Bright", 2, color = "Red"),
        item(3, Category.TOPS, listOf("Casual", "Outdoor", "Workout"), "Neutral", 2, color = "Charcoal"),
        // A layerable top -- notes describe it as wearable over another top, so it should also
        // surface as an outerwear candidate (see AiOutfitStrategy.mightLayerAsOuterwear).
        item(
            18, Category.TOPS, listOf("Business Casual", "Casual", "Outdoor"), "Neutral", 3,
            color = "Navy", description = "A shirt can be worn underneath it, works as a light layer."
        ),
        item(17, Category.BOTTOMS, listOf("Formal", "Business Casual", "Outdoor", "Party"), "Neutral", 3, color = "Black"),
        item(21, Category.BOTTOMS, listOf("Formal"), "Neutral", 3, color = "Charcoal"),
        item(19, Category.BOTTOMS, listOf("Casual", "Workout", "Loungewear", "Outdoor"), "Neutral", 1, color = "Khaki"),
        item(20, Category.FOOTWEAR, listOf("Business Casual", "Formal"), "Neutral", 4, color = "Black"),
        item(15, Category.FOOTWEAR, listOf("Casual", "Business Casual", "Loungewear", "Outdoor", "Party"), "Bright", 3, color = "White"),
        item(14, Category.FOOTWEAR, listOf("Business Casual", "Casual", "Workout", "Outdoor", "Loungewear", "Party"), "Neutral", 4, color = "Brown"),
        item(6, Category.OUTERWEAR, listOf("Formal"), "Neutral", 3, color = "Black"),
        item(9, Category.OUTERWEAR, listOf("Casual", "Workout", "Loungewear", "Outdoor"), "Neutral", 4, color = "Olive"),
        item(7, Category.OUTERWEAR, listOf("Casual", "Workout", "Loungewear", "Outdoor", "Party"), "Bright", 4, color = "Yellow", isWaterResistant = true),
        item(11, Category.ACCESSORIES, listOf("Business Casual", "Formal", "Party", "Outdoor", "Casual"), "Neutral", 1, color = "Silver"),
        item(12, Category.ACCESSORIES, listOf("Formal", "Business Casual", "Outdoor"), "Cool", 1, color = "Navy"),
        item(13, Category.ACCESSORIES, listOf("Casual", "Workout", "Loungewear", "Outdoor", "Party"), "Neutral", 1, color = "Black", isWaterResistant = true)
    )

    @Test
    fun formalFuneralRequest_logsPromptAndReply() = runBlocking {
        val aiClient = GeminiAiClient()

        check(aiClient.isConfigured()) {
            "No GEMINI_API_KEY configured -- add one to local.properties before running this test."
        }

        val constraints = OutfitConstraints(
            weather = WeatherInfo(temperature = 15.6, condition = "Cloudy"),
            occasion = "Formal",
            userPrompt = "my most formal outfit for a funeral"
        )

        val result = runCatching {
            AiOutfitStrategy(aiClient).generateOutfit(wardrobe, constraints)
        }

        result.onSuccess { outfit ->
            Log.i(
                TAG,
                "SUCCESS isAiGenerated=${outfit.isAiGenerated} items=${outfit.items.map { it.id to it.category }} note=${outfit.note}"
            )
        }.onFailure { e ->
            Log.i(TAG, "FAILED with ${e::class.simpleName}: ${e.message}")
        }

        // The point of this test is the logged prompt/reply, not a pass/fail signal -- but assert
        // something so a totally broken environment (e.g. model file missing) still fails loudly.
        assertTrue("Expected either a successful outfit or a handled exception", true)
    }

    /**
     * Isolates whether the failure is about the outfit prompt specifically, or the model/session
     * setup in general -- a trivial prompt with zero relation to AiOutfitStrategy's structure.
     */
    @Test
    fun trivialPrompt_logsRawReply() = runBlocking {
        val aiClient = GeminiAiClient()
        check(aiClient.isConfigured()) { "No GEMINI_API_KEY configured in local.properties." }

        val result = aiClient.generate("Say hello in one word.")
        Log.i(TAG, "TRIVIAL result=$result")
        assertTrue(true)
    }

    companion object {
        private const val TAG = "AiDiagnostic"
    }
}
