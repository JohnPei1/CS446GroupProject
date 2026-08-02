package com.example.wardrobeapp.data.local.dao

import androidx.room.*
import com.example.wardrobeapp.data.local.entity.OutfitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitDao {
    @Query("SELECT * FROM outfits")
    fun getAllOutfits(): Flow<List<OutfitEntity>>

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getById(id: Long): OutfitEntity?

    /** Returns the row id, so callers can schedule a freshly saved outfit. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outfit: OutfitEntity): Long

    @Delete
    suspend fun delete(outfit: OutfitEntity)
}
