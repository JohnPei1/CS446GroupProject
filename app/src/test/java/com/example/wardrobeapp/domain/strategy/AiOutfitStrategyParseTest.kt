package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
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
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "accessoryIds": [40], "reasoning": "with a chain"}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.any { it.id == 40L })
    }

    @Test
    fun passesThroughWeatherWarningWhenPresent() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "ok", "weatherWarning": "Heads up: rain is expected."}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals("Heads up: rain is expected.", result.weatherWarning)
    }

    @Test
    fun weatherWarningIsNullWhenNotPresent() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "ok"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(null, result.weatherWarning)
    }

    @Test
    fun blankWeatherWarningIsTreatedAsNull() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "ok", "weatherWarning": "   "}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(null, result.weatherWarning)
    }

    @Test
    fun omitsAccessoryWhenNotSpecified() {
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "reasoning": "no accessory needed"}"""
        val result = parseAndValidate(raw, candidates)
        assertTrue(result.items.none { it.category == Category.ACCESSORIES })
    }

    @Test
    fun includesMultipleAccessoriesTogether() {
        // e.g. a watch and a tie worn at the same time -- accessories are no longer limited to one.
        val multiAccessoryCandidates = candidates + (
            Category.ACCESSORIES to listOf(item(40, Category.ACCESSORIES), item(41, Category.ACCESSORIES))
        )
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "accessoryIds": [40, 41], "reasoning": "watch and tie"}"""
        val result = parseAndValidate(raw, multiAccessoryCandidates)
        assertEquals(setOf(40L, 41L), result.items.filter { it.category == Category.ACCESSORIES }.map { it.id }.toSet())
    }

    @Test
    fun invalidAccessoryIdIsSkippedNotForceFilled() {
        // Unlike top/bottom, an accessory id that doesn't match any candidate is just dropped --
        // there's no sensible "fall back to the top-scored accessory" for an optional multi-pick.
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "accessoryIds": [999], "reasoning": "oops"}"""
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

    @Test
    fun resolvesLayerableTopCrossListedAsOuterwear() {
        // AiOutfitStrategy.generateOutfit cross-lists layerable Tops into the Outerwear bucket
        // (still tagged category="Tops") so the model can pick one for outerwearId -- verify
        // resolution honors that id from the Outerwear candidate list regardless of its real
        // category.
        val layerableTop = item(2, Category.TOPS)
        val candidatesWithLayerableTop = candidates + (
            Category.OUTERWEAR to (candidates.getValue(Category.OUTERWEAR) + layerableTop)
        )
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "outerwearId": 2, "reasoning": "layered"}"""
        val result = parseAndValidate(raw, candidatesWithLayerableTop)
        assertTrue(result.items.any { it.id == 2L && it.category == Category.TOPS })
    }

    @Test
    fun dropsOuterwearWhenSameIdAsTop() {
        // The model echoing the same id for both topId and outerwearId would mean "wear this
        // shirt as both your top and your layer over it" -- nonsensical, so it's dropped rather
        // than shown twice.
        val layerableTop = item(1, Category.TOPS)
        val candidatesWithLayerableTop = candidates + (
            Category.OUTERWEAR to (candidates.getValue(Category.OUTERWEAR) + layerableTop)
        )
        val raw = """{"topId": 1, "bottomId": 10, "footwearId": 20, "outerwearId": 1, "reasoning": "oops"}"""
        val result = parseAndValidate(raw, candidatesWithLayerableTop)
        assertEquals(1, result.items.count { it.id == 1L })
        assertTrue(result.items.none { it.category == Category.OUTERWEAR })
    }

    @Test(expected = Exception::class)
    fun throwsOnMalformedJson() {
        parseAndValidate("not json at all", candidates)
    }

    @Test
    fun wardrobeGapSkipsIdResolutionAndReturnsNoItems() {
        // The model declared the request unsatisfiable; the required-slot force-fill must NOT
        // kick in and dress the user anyway. In production this only happens when the user typed
        // a free-text request (allowRequiredOmission derives from a non-blank userPrompt) -- a
        // gap is only trustworthy when the user gave the model something to actually refuse.
        val raw = """{"topId": null, "bottomId": null, "wardrobeGap": "Add swimwear to your wardrobe for swimming."}"""
        val result = parseAndValidate(raw, candidates, allowRequiredOmission = true)
        assertTrue(result.items.isEmpty())
        assertEquals("Add swimwear to your wardrobe for swimming.", result.wardrobeGap)
    }

    @Test
    fun wardrobeGapIsIgnoredWithoutAUserPromptToJustifyIt() {
        // Reproduces a real on-device reply: with no occasion and no typed request (a plain
        // "Generate" tap), Qwen still hedged with a nonsense non-blank wardrobeGap instead of
        // just picking from perfectly good candidates. Nothing about a generic request can
        // legitimately be unsatisfiable, so without allowRequiredOmission this must be ignored
        // and the required slots force-filled instead of bailing out.
        val raw = """{"topId": null, "bottomId": null, "wardrobeGap": "No additional footwear or outerwear requested"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(null, result.wardrobeGap)
        assertEquals(1L, result.items.first { it.category == Category.TOPS }.id)
        assertEquals(10L, result.items.first { it.category == Category.BOTTOMS }.id)
    }

    @Test
    fun blankWardrobeGapIsIgnoredAndOutfitResolvesNormally() {
        val raw = """{"topId": 1, "bottomId": 10, "wardrobeGap": "  ", "reasoning": "ok"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(listOf(1L, 10L), result.items.map { it.id })
        assertEquals(null, result.wardrobeGap)
    }

    @Test
    fun nonBlankWardrobeGapWithValidIdsIsIgnored() {
        // Reproduces a real on-device reply: the model picked valid top/bottom ids *and* also
        // filled wardrobeGap with unrelated commentary, misusing the field. Since it wasn't
        // instructed to fill wardrobeGap unless it ALSO left the required ids null, a non-null
        // id pick should win -- discarding it would throw away a perfectly good outfit.
        val raw = """{"topId": 1, "bottomId": 10, "wardrobeGap": "A neutral top pairs well here.", "reasoning": "ok"}"""
        val result = parseAndValidate(raw, candidates)
        assertEquals(listOf(1L, 10L), result.items.map { it.id })
        assertEquals(null, result.wardrobeGap)
    }

    // --- mightLayerAsOuterwear: notes-based hint that a top can double as outerwear ---

    private fun itemWithNotes(id: Long, notes: String) =
        ClothingItem(id = id, name = "Item $id", category = Category.TOPS, imagePath = "", description = notes)

    @Test
    fun mightLayerAsOuterwear_detectsUserPhrasing() {
        // The user's own real example: "a shirt can be worn underneath it".
        assertTrue(itemWithNotes(1, "A shirt can be worn underneath it").mightLayerAsOuterwear())
    }

    @Test
    fun mightLayerAsOuterwear_falseForUnrelatedNotes() {
        assertTrue(!itemWithNotes(1, "Dry clean only, runs small").mightLayerAsOuterwear())
    }

    @Test
    fun mightLayerAsOuterwear_falseForBlankNotes() {
        assertTrue(!itemWithNotes(1, "").mightLayerAsOuterwear())
    }
}
