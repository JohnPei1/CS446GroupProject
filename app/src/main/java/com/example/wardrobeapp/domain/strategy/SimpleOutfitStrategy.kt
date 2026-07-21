package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Outfit generator that scores candidates by occasion/color/variety (no weather influence). A
 * complete, socially acceptable outfit requires a top and a bottom; if the wardrobe has neither,
 * this throws [IncompleteOutfitException] rather than silently returning a partial outfit.
 */
class SimpleOutfitStrategy : OutfitStrategy {
    override suspend fun generateOutfit(
        items: List<ClothingItem>,
        constraints: OutfitConstraints
    ): Outfit {
        val selected = mutableListOf<ClothingItem>()
        val top = OutfitScorer.pickBest(items, Category.TOPS, constraints, selected)
        top?.let { selected.add(it) }
        val bottom = OutfitScorer.pickBest(items, Category.BOTTOMS, constraints, selected)
        bottom?.let { selected.add(it) }

        val missing = buildList {
            if (top == null) add(Category.TOPS)
            if (bottom == null) add(Category.BOTTOMS)
        }
        if (missing.isNotEmpty()) throw IncompleteOutfitException(missing)

        OutfitScorer.pickBest(items, Category.FOOTWEAR, constraints, selected)?.let { selected.add(it) }

        val note = constraints.occasion?.let { "Matched: $it" }
        return Outfit(name = "Everyday Outfit", items = selected, note = note)
    }
}