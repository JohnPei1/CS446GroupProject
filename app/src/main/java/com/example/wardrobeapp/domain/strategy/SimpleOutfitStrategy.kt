package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Basic outfit generator that picks items quickly.
 */
class SimpleOutfitStrategy : OutfitStrategy {
    override fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit {
        // Simple random generation logic
        return Outfit(name = "Simple Outfit", items = items.take(2))
    }
}
