package com.example.wardrobeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.wardrobeapp.data.local.dao.ClothingItemDao
import com.example.wardrobeapp.data.local.dao.OutfitDao
import com.example.wardrobeapp.data.local.dao.ScheduledOutfitDao
import com.example.wardrobeapp.data.local.entity.ClothingItemEntity
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity

@Database(
    entities = [
        ClothingItemEntity::class,
        OutfitEntity::class,
        ScheduledOutfitEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun outfitDao(): OutfitDao
    abstract fun scheduledOutfitDao(): ScheduledOutfitDao
}
