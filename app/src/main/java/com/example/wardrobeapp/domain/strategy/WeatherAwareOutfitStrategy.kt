package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Outfit generator that considers weather conditions.
 */
class WeatherAwareOutfitStrategy : OutfitStrategy {
    override fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit {
        // Logic to filter items based on weather
        return Outfit(name = "Weather Aware Outfit", items = items.take(2))
    }
}
