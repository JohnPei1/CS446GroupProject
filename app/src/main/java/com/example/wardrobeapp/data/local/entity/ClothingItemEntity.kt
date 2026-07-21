package com.example.wardrobeapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val imagePath: String,
    val brand: String,
    val color: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val season: String = "All-Season",
    val colorFamily: String = "Neutral",
    val warmthLevel: Int = 3,
    val isWaterResistant: Boolean = false,
    val isFavorite: Boolean = false,
    val timesWorn: Int = 0,
    val lastWornDate: Long? = null,
    val dateAdded: Long = System.currentTimeMillis()
)
