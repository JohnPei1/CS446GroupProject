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
    fun topCandidates_favorsOccasionMatchOverWarmthMismatchOnAnOrdinaryWarmDay() {
        // Reproduces a real reported bug: AiOutfitStrategy used to hard-filter candidates by
        // warmth mismatch before they were ever scored, which could silently drop the only
        // occasion-appropriate item (formal pants for a funeral) while a warmth-matched but
        // occasion-wrong one (shorts) survived -- resulting in an AI-generated funeral outfit in
        // shorts. Now that filtering is gone, occasion match must reliably outrank an ordinary
        // (non-extreme-weather) warmth mismatch in the ranking that AiOutfitStrategy relies on.
        val formalPants = top(id = 1, tags = listOf("Formal"), warmthLevel = 4)
        val casualShorts = top(id = 2, tags = listOf("Casual"), warmthLevel = 1)
        val ranked = OutfitScorer.topCandidates(
            items = listOf(casualShorts, formalPants),
            category = Category.TOPS,
            constraints = OutfitConstraints(
                occasion = "Formal",
                weather = WeatherInfo(temperature = 30.0, condition = "Sunny")
            ),
            alreadyPicked = emptyList(),
            limit = 2
        )
        assertEquals(formalPants.id, ranked.first().id)
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
    fun score_stronglyFavorsShortsOnAHotDayWithNoGuidanceAtAll() {
        // Reproduces the reported bug directly: a completely bare Generate tap (no occasion, no
        // typed request) was recommending shorts on a cool day and not on a hot one -- the old
        // weight only netted a few points either way, easily lost to other scoring factors.
        val shorts = top(id = 1, warmthLevel = 1)
        val heavierPants = top(id = 2, warmthLevel = 3)
        val hotDay = OutfitConstraints(weather = WeatherInfo(temperature = 32.0, condition = "Sunny"))
        assertTrue(OutfitScorer.score(shorts, hotDay, emptyList()) > OutfitScorer.score(heavierPants, hotDay, emptyList()) + 20)
    }

    @Test
    fun score_stronglyAvoidsShortsOnACoolDayWithNoGuidanceAtAll() {
        val shorts = top(id = 1, warmthLevel = 1)
        val heavierPants = top(id = 2, warmthLevel = 3)
        val coolDay = OutfitConstraints(weather = WeatherInfo(temperature = 15.0, condition = "Cloudy"))
        assertTrue(OutfitScorer.score(heavierPants, coolDay, emptyList()) > OutfitScorer.score(shorts, coolDay, emptyList()) + 20)
    }

    @Test
    fun score_occasionAloneStillUsesTheSofterWeatherWeight() {
        // An occasion chip with no typed text must still count as "guidance", not "no guidance"
        // -- otherwise this reintroduces the earlier fixed bug where a Formal occasion lost to
        // weather-matched shorts. Mirrors topCandidates_favorsOccasionMatchOverWarmthMismatch...
        val formalPants = top(id = 1, tags = listOf("Formal"), warmthLevel = 4)
        val casualShorts = top(id = 2, tags = listOf("Casual"), warmthLevel = 1)
        val hotDay = OutfitConstraints(occasion = "Formal", weather = WeatherInfo(temperature = 30.0, condition = "Sunny"))
        assertTrue(OutfitScorer.score(formalPants, hotDay, emptyList()) > OutfitScorer.score(casualShorts, hotDay, emptyList()))
    }

    @Test
    fun pickBest_neverPicksHeavyItemInHotWeatherWhenAlternativeExists() {
        // Reproduces the reported bug: a warmth-5 "Big Heavy coat" must not surface on a hot
        // day just because tie-randomization or other bonuses favored it.
        val lightTop = top(id = 1, warmthLevel = 1)
        val parka = top(id = 2, warmthLevel = 5)
        val constraints = OutfitConstraints(weather = WeatherInfo(temperature = 30.0, condition = "Sunny"))
        repeat(20) {
            val picked = OutfitScorer.pickBest(listOf(parka, lightTop), Category.TOPS, constraints, emptyList())
            assertEquals(lightTop.id, picked?.id)
        }
    }

    @Test
    fun isExtremeWarmthMismatch_flagsParkaInHeatButNotAdjacentLevels() {
        val hot = WeatherInfo(temperature = 30.0, condition = "Sunny") // ideal warmth = 1
        assertTrue(OutfitScorer.isExtremeWarmthMismatch(top(id = 1, warmthLevel = 5), hot))
        assertTrue(OutfitScorer.isExtremeWarmthMismatch(top(id = 2, warmthLevel = 4), hot))
        assertTrue(!OutfitScorer.isExtremeWarmthMismatch(top(id = 3, warmthLevel = 2), hot))

        val freezing = WeatherInfo(temperature = -10.0, condition = "Snow") // ideal warmth = 5
        assertTrue(OutfitScorer.isExtremeWarmthMismatch(top(id = 4, warmthLevel = 1), freezing))
        assertTrue(!OutfitScorer.isExtremeWarmthMismatch(top(id = 5, warmthLevel = 4), freezing))
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

    @Test
    fun excludeRecentlyUsedItems_dropsExcludedIdsWhenAlternativesExist() {
        val scheduledTop = top(id = 1)
        val freeTop = top(id = 2)
        val filtered = OutfitScorer.excludeRecentlyUsedItems(listOf(scheduledTop, freeTop), excludedIds = setOf(1L))
        assertEquals(listOf(freeTop.id), filtered.map { it.id })
    }

    @Test
    fun excludeRecentlyUsedItems_fallsBackPerCategoryWhenExclusionWouldEmptyIt() {
        // Only one top exists and it's excluded -- excluding it would make generation
        // impossible, so it's kept despite being "recently used".
        val onlyTop = top(id = 1)
        val freeBottom = ClothingItem(id = 2, name = "Bottom", category = Category.BOTTOMS, imagePath = "")
        val scheduledBottom = ClothingItem(id = 3, name = "Bottom 2", category = Category.BOTTOMS, imagePath = "")
        val filtered = OutfitScorer.excludeRecentlyUsedItems(
            listOf(onlyTop, freeBottom, scheduledBottom),
            excludedIds = setOf(1L, 3L)
        )
        assertTrue(filtered.any { it.id == 1L }) // kept: excluding it would empty Tops entirely
        assertEquals(listOf(2L), filtered.filter { it.category == Category.BOTTOMS }.map { it.id }) // dropped: an alternative exists
    }

    @Test
    fun excludeRecentlyUsedItems_returnsWardrobeUnchangedWithNoExclusions() {
        val wardrobe = listOf(top(id = 1), top(id = 2))
        assertEquals(wardrobe, OutfitScorer.excludeRecentlyUsedItems(wardrobe, emptySet()))
    }

    @Test
    fun excludeRecentlyUsedItems_neverEmptiesASmallCategoryEvenWhenFullySaturated() {
        // Reproduces the reported scenario: a 4-item Accessories wardrobe where all 4 have
        // cycled through the recent-picks history -- must never block suggestions entirely.
        val accessories = (1L..4L).map {
            ClothingItem(id = it, name = "Accessory $it", category = Category.ACCESSORIES, imagePath = "")
        }
        val filtered = OutfitScorer.excludeRecentlyUsedItems(accessories, excludedIds = setOf(1L, 2L, 3L, 4L))
        assertEquals(4, filtered.size)
    }

    @Test
    fun occasionMatchesOuterwear_trueWhenTagged() {
        val blazer = top(id = 1, tags = listOf("Formal")).copy(category = Category.OUTERWEAR)
        assertTrue(OutfitScorer.occasionMatchesOuterwear(listOf(blazer), "Formal"))
    }

    @Test
    fun occasionMatchesOuterwear_falseWhenNoMatchingOuterwear() {
        val windbreaker = top(id = 1, tags = listOf("Outdoor")).copy(category = Category.OUTERWEAR)
        assertTrue(!OutfitScorer.occasionMatchesOuterwear(listOf(windbreaker), "Formal"))
    }

    @Test
    fun occasionMatchesOuterwear_falseWithoutAnOccasion() {
        val blazer = top(id = 1, tags = listOf("Formal")).copy(category = Category.OUTERWEAR)
        assertTrue(!OutfitScorer.occasionMatchesOuterwear(listOf(blazer), null))
    }

    @Test
    fun pickBest_matchesOccasionSynonymInFreeTextPrompt() {
        // The app's own prompt placeholder suggests "job interview" as an example -- "interview"
        // never literally matches a "Business Casual" tag, so without synonym awareness this
        // item would get zero signal and could easily lose to an unrelated casual item.
        val casual = top(id = 1, tags = listOf("Casual"))
        val businessCasual = top(id = 2, tags = listOf("Business Casual"))
        val constraints = OutfitConstraints(userPrompt = "dressing for a job interview")
        repeat(20) {
            val picked = OutfitScorer.pickBest(listOf(casual, businessCasual), Category.TOPS, constraints, emptyList())
            assertEquals(businessCasual.id, picked?.id)
        }
    }

    @Test
    fun pickBest_matchesFuneralSynonymToFormal() {
        val casual = top(id = 1, tags = listOf("Casual"))
        val formal = top(id = 2, tags = listOf("Formal"))
        val constraints = OutfitConstraints(userPrompt = "my most formal outfit for a funeral")
        repeat(20) {
            val picked = OutfitScorer.pickBest(listOf(casual, formal), Category.TOPS, constraints, emptyList())
            assertEquals(formal.id, picked?.id)
        }
    }

    private fun accessory(
        id: Long,
        name: String = "Item $id",
        color: String = "",
        tags: List<String> = emptyList()
    ) = ClothingItem(id = id, name = name, category = Category.ACCESSORIES, imagePath = "", color = color, tags = tags)
}
