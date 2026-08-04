package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SimpleOutfitStrategyTest {

    private fun item(id: Long, category: String) =
        ClothingItem(id = id, name = "Item $id", category = category, imagePath = "")

    @Test
    fun generateOutfit_picksOneItemPerCoreCategory() = runBlocking {
        val wardrobe = listOf(
            item(1, Category.TOPS),
            item(2, Category.BOTTOMS),
            item(3, Category.FOOTWEAR),
            item(4, Category.OUTERWEAR)
        )
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
        assertEquals(3, outfit.items.size)
        assertEquals(setOf(Category.TOPS, Category.BOTTOMS, Category.FOOTWEAR), outfit.items.map { it.category }.toSet())
    }

    @Test
    fun generateOutfit_noteReflectsRequestedOccasion() = runBlocking {
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), item(3, Category.FOOTWEAR))
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints(occasion = "Workout"))
        assertTrue(outfit.note?.contains("Workout") == true)
    }

    @Test
    fun generateOutfit_noteFallsBackToGenericExplanationWithNoOccasion() = runBlocking {
        // An outfit should never be shown with no explanation at all -- even with no occasion/
        // weather/prompt signal, there should be some rationale text.
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), item(3, Category.FOOTWEAR))
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
        assertTrue(outfit.note?.isNotBlank() == true)
    }

    @Test
    fun generateOutfit_includesAccessoryOnlyWhenPromptMatches() = runBlocking {
        val chain = ClothingItem(id = 5, name = "Gold Chain", category = Category.ACCESSORIES, imagePath = "", color = "Gold")
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), chain)

        val withoutPrompt = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
        assertTrue(withoutPrompt.items.none { it.category == Category.ACCESSORIES })

        val withPrompt = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints(userPrompt = "Using gold chain"))
        assertTrue(withPrompt.items.any { it.id == chain.id })
    }

    @Test
    fun generateOutfit_includesOuterwearWhenItMatchesTheOccasion() = runBlocking {
        // This strategy never considered outerwear at all before -- a Formal request with
        // weather-aware off could never get a blazer, regardless of the wardrobe.
        val blazer = ClothingItem(id = 4, name = "Blazer", category = Category.OUTERWEAR, imagePath = "", tags = listOf("Formal"))
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), blazer)
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints(occasion = "Formal"))
        assertTrue(outfit.items.any { it.id == blazer.id })
    }

    @Test
    fun generateOutfit_omitsOuterwearWhenItDoesNotMatchTheOccasion() = runBlocking {
        val windbreaker = ClothingItem(id = 4, name = "Windbreaker", category = Category.OUTERWEAR, imagePath = "", tags = listOf("Outdoor"))
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), windbreaker)
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints(occasion = "Formal"))
        assertTrue(outfit.items.none { it.category == Category.OUTERWEAR })
    }

    @Test
    fun generateOutfit_throwsWhenBottomsMissing() = runBlocking {
        // Reproduces the reported "top+bottom+outerwear, no footwear" wardrobe shape, minus the
        // bottom, to confirm a missing required category is now a clear error, not a silent
        // top-only outfit.
        val wardrobe = listOf(item(1, Category.TOPS), item(4, Category.OUTERWEAR))
        try {
            SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
            fail("expected IncompleteOutfitException")
        } catch (e: IncompleteOutfitException) {
            assertEquals(listOf(Category.BOTTOMS), e.missingCategories)
        }
    }

    @Test
    fun generateOutfit_includesBothTopAndBottomWhenBothAvailable() = runBlocking {
        // The exact scenario reported as "wonky": one top, one bottom, one outerwear, no footwear.
        val wardrobe = listOf(item(1, Category.TOPS), item(2, Category.BOTTOMS), item(4, Category.OUTERWEAR))
        val outfit = SimpleOutfitStrategy().generateOutfit(wardrobe, OutfitConstraints())
        assertTrue(outfit.items.any { it.category == Category.TOPS })
        assertTrue(outfit.items.any { it.category == Category.BOTTOMS })
    }
}
