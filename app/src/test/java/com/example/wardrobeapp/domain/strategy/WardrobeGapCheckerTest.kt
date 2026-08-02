package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WardrobeGapCheckerTest {

    private fun item(
        id: Long,
        category: String,
        tags: List<String> = emptyList(),
        warmthLevel: Int = 3
    ) = ClothingItem(
        id = id,
        name = "Item $id",
        category = category,
        imagePath = "",
        tags = tags,
        warmthLevel = warmthLevel
    )

    private val casualWardrobe = listOf(
        item(1, Category.TOPS, tags = listOf("Casual")),
        item(2, Category.BOTTOMS, tags = listOf("Casual"))
    )

    @Test
    fun findGaps_flagsOccasionWithNoTaggedItems() {
        // Reproduces the reported bug: choosing Swim with no swimwear used to silently
        // generate a normal outfit instead of telling the user to add swim pieces.
        val gaps = WardrobeGapChecker.findGaps(casualWardrobe, OutfitConstraints(occasion = "Swim"))
        assertEquals(1, gaps.size)
        assertTrue(gaps.first().contains("Swim"))
    }

    @Test
    fun findGaps_acceptsOccasionWithAtLeastOneTaggedItem() {
        val withTrunks = casualWardrobe + item(3, Category.BOTTOMS, tags = listOf("Swim"))
        val gaps = WardrobeGapChecker.findGaps(withTrunks, OutfitConstraints(occasion = "Swim"))
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun findGaps_flagsFreezingWeatherWithOnlySummerClothes() {
        val summerOnly = listOf(
            item(1, Category.TOPS, warmthLevel = 1),
            item(2, Category.BOTTOMS, warmthLevel = 1)
        )
        val gaps = WardrobeGapChecker.findGaps(
            summerOnly,
            OutfitConstraints(weather = WeatherInfo(temperature = -30.0, condition = "Snow"))
        )
        // Both tops and bottoms are hopeless at -30°C.
        assertEquals(2, gaps.size)
        assertTrue(gaps.all { it.contains("-30") })
    }

    @Test
    fun findGaps_acceptsFreezingWeatherWithOneWarmEnoughOption() {
        val mixed = listOf(
            item(1, Category.TOPS, warmthLevel = 1),
            item(2, Category.TOPS, warmthLevel = 5),
            item(3, Category.BOTTOMS, warmthLevel = 4)
        )
        val gaps = WardrobeGapChecker.findGaps(
            mixed,
            OutfitConstraints(weather = WeatherInfo(temperature = -30.0, condition = "Snow"))
        )
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun findGaps_emptyWithoutOccasionOrWeatherConstraints() {
        val gaps = WardrobeGapChecker.findGaps(casualWardrobe, OutfitConstraints())
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun findGaps_ignoresEmptyCategoriesForWeather() {
        // A missing category entirely is IncompleteOutfitException's job, not a warmth gap.
        val topsOnly = listOf(item(1, Category.TOPS, warmthLevel = 5))
        val gaps = WardrobeGapChecker.findGaps(
            topsOnly,
            OutfitConstraints(weather = WeatherInfo(temperature = -30.0, condition = "Snow"))
        )
        assertTrue(gaps.isEmpty())
    }
}
