package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOutfitStrategyParseTest {

    private fun item(id: Long, category: String, warmthLevel: Int = 3) =
        ClothingItem(id = id, name = "Item $id", category = category, imagePath = "", warmthLevel = warmthLevel)

    private val candidates = mapOf(
        Category.TOPS to listOf(item(1, Category.TOPS), item(2, Category.TOPS)),
        Category.BOTTOMS to listOf(item(10, Category.BOTTOMS)),
        Category.FOOTWEAR to listOf(item(20, Category.FOOTWEAR)),
        Category.OUTERWEAR to listOf(item(30, Category.OUTERWEAR)),
        Category.ACCESSORIES to listOf(item(40, Category.ACCESSORIES))
    )

    @Test
    fun includesAccessoryWhenSpecified() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "accessoryId": 40, "reasoning": "with a chain"}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.any { it.id == 40L })
    }

    @Test
    fun omitsAccessoryWhenNotSpecified() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "no accessory needed"}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.none { it.category == Category.ACCESSORIES })
    }

    @Test
    fun parsesValidJsonReply() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "outerwearId": null, "reasoning": "Casual and warm"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(listOf(1L, 10L, 20L), result.items.map { it.id })
        assertEquals("Casual and warm", result.reasoning)
    }

    @Test
    fun stripsMarkdownCodeFencesBeforeParsing() {
        val raw = "```json\n{\"topId\": 1, \"bottomId\": 10, \"footwearId\": 20, \"reasoning\": \"ok\"}\n```"
        val result = parseAndValidate(raw, candidates)
        assertEquals(listOf(1L, 10L, 20L), result.items.map { it.id })
    }

    @Test
    fun fallsBackToTopCandidateForIdOutsideCategorySet() {
        val raw = """{"topId": 999, "bottomId": 10, "footwearId": 20, "reasoning": "oops"}"""
        val result = parseAndValidate(raw, candidates)
        // 999 isn't a valid Tops id -> falls back to the first Tops candidate (id 1)
        assertEquals(1L, result.items.first { it.category == Category.TOPS }.id)
    }

    @Test
    fun fallsBackToTopCandidateForMissingSlotByDefault() {
        // No allowRequiredOmission -> a missing/null required slot is treated as a model mistake,
        // not a deliberate choice, and gets force-filled -- otherwise a truncated reply from a
        // small on-device model could silently produce a "pants only" outfit.
        val raw = """{"bottomId": 10, "footwearId": 20, "reasoning": "missing top"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(1L, result.items.first { it.category == Category.TOPS }.id)
    }

    @Test
    fun honorsRequiredSlotOmissionWhenAllowed() {
        // With allowRequiredOmission=true (the user typed a prompt), a deliberate null top is
        // trusted -- e.g. the user asked for a swim-only outfit.
        val raw = """{"bottomId": 10, "footwearId": 20, "reasoning": "swimming, no shirt needed"}"""
        val result = parseAndValidate(raw, candidates, allowRequiredOmission = true)
        assertTrue(result.items.none { it.category == Category.TOPS })
        assertEquals(listOf(10L, 20L), result.items.map { it.id })
    }

    @Test
    fun omitsOuterwearWhenNotSpecified() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "no jacket needed"}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.none { it.category == Category.OUTERWEAR })
    }

    @Test(expected = Exception::class)
    fun throwsOnMalformedJson() {
        parseAndValidate("not json at all", candidates)
    }

    @Test
    fun wardrobeGapSkipsIdResolutionAndReturnsNoItems() {
        // The model declared the request unsatisfiable; the required-slot force-fill must NOT
        // kick in and dress the user anyway.
        val raw = """{"topId": null, "bottomId": null, "wardrobeGap": "Add swimwear to your wardrobe for swimming."}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.isEmpty())
        assertEquals("Add swimwear to your wardrobe for swimming.", result.wardrobeGap)
    }

    @Test
    fun blankWardrobeGapIsIgnoredAndOutfitResolvesNormally() {
        val raw = """{"topId": 1, "bottomId": 10, "wardrobeGap": "  ", "reasoning": "ok"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(listOf(1L, 10L), result.items.map { it.id })
        assertEquals(null, result.wardrobeGap)
    }

    // --- weatherAppropriateShortlist: keeps wildly weather-wrong items away from the model ---

    private val hotDay = WeatherInfo(temperature = 30.0, condition = "Sunny")

    @Test
    fun shortlist_dropsParkaFromOptionalCategoryInHeat() {
        val parka = item(30, Category.OUTERWEAR, warmthLevel = 5)
        val filtered = weatherAppropriateShortlist(listOf(parka), hotDay, required = false)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun shortlist_keepsRequiredCategoryEvenIfAllCandidatesMismatch() {
        // The user only owns heavy tops: they still have to wear something.
        val heavyTop = item(1, Category.TOPS, warmthLevel = 5)
        val filtered = weatherAppropriateShortlist(listOf(heavyTop), hotDay, required = true)
        assertEquals(listOf(heavyTop.id), filtered.map { it.id })
    }

    @Test
    fun shortlist_dropsOnlyTheMismatchedCandidates() {
        val lightTop = item(1, Category.TOPS, warmthLevel = 1)
        val heavyTop = item(2, Category.TOPS, warmthLevel = 5)
        val filtered = weatherAppropriateShortlist(listOf(lightTop, heavyTop), hotDay, required = true)
        assertEquals(listOf(lightTop.id), filtered.map { it.id })
    }

    @Test
    fun shortlist_passesThroughUnchangedWithoutWeather() {
        val parka = item(30, Category.OUTERWEAR, warmthLevel = 5)
        val filtered = weatherAppropriateShortlist(listOf(parka), weather = null, required = false)
        assertEquals(listOf(parka.id), filtered.map { it.id })
    }
}
