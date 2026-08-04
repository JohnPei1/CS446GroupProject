package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Outfit generator that scores candidates by occasion/color/variety and factors in weather:
 * warmth-appropriateness, rain/water-resistance fit, and an outerwear layer when it's cold. A
 * complete, socially acceptable outfit requires a top and a bottom; if the wardrobe has neither,
 * this throws [IncompleteOutfitException] rather than silently returning a partial outfit.
 */
class WeatherAwareOutfitStrategy : OutfitStrategy {
    override suspend fun generateOutfit(
        items: List<ClothingItem>,
        constraints: OutfitConstraints
    ): Outfit {
        val temperature = constraints.weather?.temperature
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
        if (temperature != null && temperature <= COLD_THRESHOLD_CELSIUS) {
            OutfitScorer.pickBest(items, Category.OUTERWEAR, constraints, selected)?.let { selected.add(it) }
        }
        OutfitScorer.matchedAccessory(items, constraints)?.let { selected.add(it) }

        val name = when {
            temperature == null -> "Everyday Outfit"
            temperature <= COLD_THRESHOLD_CELSIUS -> "Cold Weather Outfit"
            temperature >= HOT_THRESHOLD_CELSIUS -> "Warm Weather Outfit"
            else -> "Everyday Outfit"
        }
        val note = OutfitScorer.buildNote(
            constraints,
            extra = listOfNotNull(temperature?.let { "sized for about ${it.toInt()}°C weather" })
        )
        return Outfit(name = name, items = selected, note = note)
    }

    companion object {
        private const val COLD_THRESHOLD_CELSIUS = 12.0
        private const val HOT_THRESHOLD_CELSIUS = 24.0
    }
}