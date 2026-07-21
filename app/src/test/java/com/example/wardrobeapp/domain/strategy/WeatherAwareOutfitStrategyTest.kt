package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WeatherInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAwareOutfitStrategyTest {

    private fun item(id: Long, category: String) =
        ClothingItem(id = id, name = "Item $id", category = category, imagePath = "")

    private val wardrobe = listOf(
        item(1, Category.TOPS),
        item(2, Category.BOTTOMS),
        item(3, Category.FOOTWEAR),
        item(4, Category.OUTERWEAR)
    )

    @Test
    fun generateOutfit_addsOuterwearLayerWhenCold() = runBlocking {
        val outfit = WeatherAwareOutfitStrategy().generateOutfit(
            wardrobe, OutfitConstraints(weather = WeatherInfo(temperature = 0.0, condition = "Clear"))
        )
        assertTrue(outfit.items.any { it.category == Category.OUTERWEAR })
        assertEquals("Cold Weather Outfit", outfit.name)
    }

    @Test
    fun generateOutfit_omitsOuterwearLayerWhenWarm() = runBlocking {
        val outfit = WeatherAwareOutfitStrategy().generateOutfit(
            wardrobe, OutfitConstraints(weather = WeatherInfo(temperature = 28.0, condition = "Sunny"))
        )
        assertTrue(outfit.items.none { it.category == Category.OUTERWEAR })
        assertEquals("Warm Weather Outfit", outfit.name)
    }

    @Test
    fun generateOutfit_everydayOutfitNameWithoutWeather() = runBlocking {
        val outfit = WeatherAwareOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
        assertEquals("Everyday Outfit", outfit.name)
    }
}
