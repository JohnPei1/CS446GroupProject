package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints

/**
 * Strategy interface for outfit generation.
 */
interface OutfitStrategy {
    suspend fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit
}

/** Category names **/
object Category {
    const val TOPS = "Tops"
    const val BOTTOMS = "Bottoms"
    const val FOOTWEAR = "Footwear"
    const val OUTERWEAR = "Outerwear"
}

/**
 * Thrown when a wardrobe has zero items in a category required for a complete, socially
 * acceptable outfit (currently: Tops, Bottoms) -- a data gap, not a generation failure.
 * Callers should surface [missingCategories] to the user rather than showing a partial outfit.
 */
class IncompleteOutfitException(val missingCategories: List<String>) :
    Exception("Missing items in: ${missingCategories.joinToString(", ")}")