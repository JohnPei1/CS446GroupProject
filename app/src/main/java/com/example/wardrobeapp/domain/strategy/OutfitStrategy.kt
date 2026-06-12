package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Strategy interface for outfit generation.
 */
interface OutfitStrategy {
    fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit
}
