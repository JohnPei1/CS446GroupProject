package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Outfit generator that considers weather conditions.
 */
class WeatherAwareOutfitStrategy : OutfitStrategy {
    override fun generateOutfit(
        items: List<ClothingItem>,
        constraints: OutfitConstraints
    ): Outfit {
        val temperature = constraints.weather?.temperature
        val selected = buildList {
            pickRandom(items, Category.TOPS)?.let { add(it) }
            pickRandom(items, Category.BOTTOMS)?.let { add(it) }
            pickRandom(items, Category.FOOTWEAR)?.let { add(it) }
            if (temperature != null && temperature <= COLD_THRESHOLD_CELSIUS) {
                pickRandom(items, Category.OUTERWEAR)?.let { add(it) }
            }
        }

        val name = when {
            temperature == null -> "Everyday Outfit"
            temperature <= COLD_THRESHOLD_CELSIUS -> "Cold Weather Outfit"
            temperature >= HOT_THRESHOLD_CELSIUS -> "Warm Weather Outfit"
            else -> "Everyday Outfit"
        }
        return Outfit(name = name, items = selected)
    }

    /** Returns category item randomly. */
    private fun pickRandom(items: List<ClothingItem>, category: String): ClothingItem? =
        items.filter { it.category.equals(category, ignoreCase = true) }.randomOrNull()
    companion object {
        private const val COLD_THRESHOLD_CELSIUS = 12.0
        private const val HOT_THRESHOLD_CELSIUS = 24.0
    }
}