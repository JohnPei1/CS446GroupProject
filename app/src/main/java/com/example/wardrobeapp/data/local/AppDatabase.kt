package com.example.wardrobeapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "wardrobe_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
