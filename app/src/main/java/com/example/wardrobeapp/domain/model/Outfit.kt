package com.example.wardrobeapp.domain.model

/**
 * Domain model for an outfit.
 */
data class Outfit(
    val id: Long = 0,
    val name: String,
    val items: List<ClothingItem>,
    val note: String? = null,
    val isAiGenerated: Boolean = false
)
