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
    const val ACCESSORIES = "Accessories"
    val ALL = listOf(TOPS, BOTTOMS, FOOTWEAR, OUTERWEAR, ACCESSORIES)
}

/**
 * Thrown when a wardrobe has zero items in a category required for a complete, socially
 * acceptable outfit (currently: Tops, Bottoms) -- a data gap, not a generation failure.
 * Callers should surface [missingCategories] to the user rather than showing a partial outfit.
 */
class IncompleteOutfitException(val missingCategories: List<String>) :
    Exception("Missing items in: ${missingCategories.joinToString(", ")}")

/**
 * Thrown when the wardrobe cannot honestly satisfy the request (no swimwear for a Swim
 * occasion, nothing warm enough for -30°C, ...). Rather than forcing an unsuitable "normal"
 * outfit, callers should surface [gaps] and suggest expanding the wardrobe.
 */
class WardrobeGapException(val gaps: List<String>) :
    Exception(gaps.joinToString("\n"))