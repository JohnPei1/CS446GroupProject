package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WarmthLevels

/**
 * Detects requests the wardrobe cannot honestly satisfy BEFORE generation runs, so the user is
 * told to expand their wardrobe instead of being handed an unsuitable "normal" outfit. Runs for
 * every generation path (deterministic and AI) -- it needs no model. The AI prompt additionally
 * covers gaps only it can understand, like free-text requests ("going swimming").
 */
object WardrobeGapChecker {

    /** Human-readable gap descriptions; empty when generation can proceed. */
    fun findGaps(items: List<ClothingItem>, constraints: OutfitConstraints): List<String> {
        val gaps = mutableListOf<String>()

        // Occasion gap: the user picked an occasion no item is tagged with (e.g. Swim with no
        // swimwear). One tagged item anywhere is enough to proceed -- scoring will favor it.
        constraints.occasion?.let { occasion ->
            val anyTagged = items.any { item ->
                item.tags.any { it.equals(occasion, ignoreCase = true) }
            }
            if (!anyTagged) {
                gaps += "Nothing in your wardrobe is tagged \"$occasion\". " +
                    "Add some $occasion pieces (and tag them) to generate an outfit for it."
            }
        }

        // Warmth gap: every top (or bottom) is wildly wrong for the temperature -- e.g. -30°C
        // with only summer clothes. Empty categories are IncompleteOutfitException's job.
        constraints.weather?.let { weather ->
            val ideal = WarmthLevels.idealFor(weather.temperature)
            for (category in listOf(Category.TOPS, Category.BOTTOMS)) {
                val inCategory = items.filter { it.category.equals(category, ignoreCase = true) }
                if (inCategory.isNotEmpty() &&
                    inCategory.all { OutfitScorer.isExtremeWarmthMismatch(it, weather) }
                ) {
                    gaps += "None of your ${category.lowercase()} suit " +
                        "${weather.temperature.toInt()}°C weather. Add pieces around warmth " +
                        "level $ideal (best for ${WarmthLevels.temperatureRangeLabel(ideal)})."
                }
            }
        }

        return gaps
    }
}
