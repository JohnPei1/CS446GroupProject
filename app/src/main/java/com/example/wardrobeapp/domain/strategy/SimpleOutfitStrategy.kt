package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Basic outfit generator that picks items quickly.
 */
class SimpleOutfitStrategy : OutfitStrategy {
    override fun generateOutfit(
        items: List<ClothingItem>,
        constraints: OutfitConstraints
    ): Outfit {
        val selected = buildList {
            pickRandom(items, Category.TOPS)?.let { add(it) }
            pickRandom(items, Category.BOTTOMS)?.let { add(it) }
            pickRandom(items, Category.FOOTWEAR)?.let { add(it) }
        }
        return Outfit(name = "Everyday Outfit", items = selected) }

    /** Returns category item randomly */
    private fun pickRandom(items: List<ClothingItem>, category: String): ClothingItem? =
        items.filter { it.category.equals(category, ignoreCase = true) }.randomOrNull() }