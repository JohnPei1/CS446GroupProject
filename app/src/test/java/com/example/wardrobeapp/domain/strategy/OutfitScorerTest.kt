package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitScorerTest {

    private fun top(
        id: Long,
        name: String = "Item $id",
        color: String = "",
        tags: List<String> = emptyList(),
        colorFamily: String = "Neutral",
        warmthLevel: Int = 3,
        isWaterResistant: Boolean = false,
        timesWorn: Int = 0,
        lastWornDate: Long? = null
    ) = ClothingItem(
        id = id,
        name = name,
        category = Category.TOPS,
        imagePath = "",
        color = color,
        tags = tags,
        colorFamily = colorFamily,
        warmthLevel = warmthLevel,
        isWaterResistant = isWaterResistant,
        timesWorn = timesWorn,
        lastWornDate = lastWornDate
    )

    @Test
    fun topCandidates_ranksOccasionMatchAboveNonMatch() {
        val casual = top(id = 1, tags = listOf("Casual"))
        val formal = top(id = 2, tags = listOf("Formal"))
        val ranked = OutfitScorer.topCandidates(
            items = listOf(formal, casual),
            category = Category.TOPS,
            constraints = OutfitConstraints(occasion = "Casual"),
            alreadyPicked = emptyList(),
            limit = 2
        )
        assertEquals(casual.id, ranked.first().id)
    }

    @Test
    fun topCandidates_ranksWarmerItemAboveLighterItemInColdWeather() {
        val light = top(id = 1, warmthLevel = 1)
        val heavy = top(id = 2, warmthLevel = 5)
        val ranked = OutfitScorer.topCandidates(
            items = listOf(light, heavy),
            category = Category.TOPS,
            constraints = OutfitConstraints(weather = WeatherInfo(temperature = -5.0, condition = "Clear")),
            alreadyPicked = emptyList(),
            limit = 2
        )
        assertEquals(heavy.id, ranked.first().id)
    }

    @Test
    fun topCandidates_penalizesRecentlyWornItems() {
        val fresh = top(id = 1, timesWorn = 0, lastWornDate = null)
        val recentlyWorn = top(id = 2, timesWorn = 10, lastWornDate = System.currentTimeMillis())
        val ranked = OutfitScorer.topCandidates(
            items = listOf(recentlyWorn, fresh),
            category = Category.TOPS,
            constraints = OutfitConstraints(),
            alreadyPicked = emptyList(),
            limit = 2
        )
        assertEquals(fresh.id, ranked.first().id)
    }

    @Test
    fun pickBest_choosesClearPromptMatchOverOtherCandidatesEveryTime() {
        // Reproduces the reported bug: with only a blue and a red top in the wardrobe, "Try
        // Again" used to pick randomly between them even after typing "red and black fit".
        val blue = top(id = 1, name = "Blue Shirt", color = "Blue", colorFamily = "Cool")
        val red = top(id = 2, name = "Red Shirt", color = "Red", colorFamily = "Warm")
        val constraints = OutfitConstraints(userPrompt = "red and black fit")
        repeat(20) {
            val picked = OutfitScorer.pickBest(listOf(blue, red), Category.TOPS, constraints, emptyList())
            assertEquals(red.id, picked?.id)
        }
    }

    @Test
    fun pickBest_hasNoPromptMatchPreferenceWithoutAPrompt() {
        val blue = top(id = 1, name = "Blue Shirt", color = "Blue")
        val red = top(id = 2, name = "Red Shirt", color = "Red")
        val picked = OutfitScorer.pickBest(listOf(blue, red), Category.TOPS, OutfitConstraints(), emptyList())
        assertTrue(picked?.id == blue.id || picked?.id == red.id)
    }

    @Test
    fun topCandidates_returnsEmptyListWhenNoItemsInCategory() {
        val ranked = OutfitScorer.topCandidates(
            items = emptyList(),
            category = Category.TOPS,
            constraints = OutfitConstraints(),
            alreadyPicked = emptyList(),
            limit = 3
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun pickBest_avoidsRepeatingLastGenerationsPickWhenAnAlternativeExists() {
        // Reproduces "keeps giving the same outfit": two otherwise-tied tops, one flagged as
        // recently shown, should reliably favor the untouched one.
        val shownLastTime = top(id = 1)
        val alternative = top(id = 2)
        val constraints = OutfitConstraints(recentItemIds = setOf(1L))
        repeat(20) {
            val picked = OutfitScorer.pickBest(listOf(shownLastTime, alternative), Category.TOPS, constraints, emptyList())
            assertEquals(alternative.id, picked?.id)
        }
    }

    @Test
    fun matchedAccessory_returnsNullWithoutPromptOrOccasion() {
        val chain = accessory(id = 1, name = "Gold Chain", color = "Gold")
        val picked = OutfitScorer.matchedAccessory(listOf(chain), OutfitConstraints())
        assertEquals(null, picked)
    }

    @Test
    fun matchedAccessory_returnsItemOnPromptKeywordMatch() {
        // Reproduces the reported bug: typing "gold chain" should surface the Accessories item,
        // which no strategy previously considered at all.
        val chain = accessory(id = 1, name = "Gold Chain", color = "Gold")
        val belt = accessory(id = 2, name = "Leather Belt", color = "Brown")
        val picked = OutfitScorer.matchedAccessory(listOf(belt, chain), OutfitConstraints(userPrompt = "Using gold chain"))
        assertEquals(chain.id, picked?.id)
    }

    @Test
    fun matchedAccessory_returnsItemOnOccasionTagMatch() {
        val formalWatch = accessory(id = 1, name = "Watch", tags = listOf("Formal"))
        val picked = OutfitScorer.matchedAccessory(listOf(formalWatch), OutfitConstraints(occasion = "Formal"))
        assertEquals(formalWatch.id, picked?.id)
    }

    @Test
    fun buildNote_isNeverBlank() {
        assertTrue(OutfitScorer.buildNote(OutfitConstraints()).isNotBlank())
    }

    private fun accessory(
        id: Long,
        name: String = "Item $id",
        color: String = "",
        tags: List<String> = emptyList()
    ) = ClothingItem(id = id, name = name, category = Category.ACCESSORIES, imagePath = "", color = color, tags = tags)
}
