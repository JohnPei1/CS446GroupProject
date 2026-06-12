package com.example.wardrobeapp.domain.model

/**
 * Domain model for a clothing item.
 */
data class ClothingItem(
    val id: Long = 0,
    val name: String,
    val category: String,
    val imagePath: String
)
