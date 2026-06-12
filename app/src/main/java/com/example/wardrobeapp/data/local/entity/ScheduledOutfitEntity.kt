package com.example.wardrobeapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_outfits")
data class ScheduledOutfitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val outfitId: Long,
    val date: Long // Unix timestamp
)
