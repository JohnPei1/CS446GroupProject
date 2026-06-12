package com.example.wardrobeapp.data.local.dao

import androidx.room.*
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledOutfitDao {
    @Query("SELECT * FROM scheduled_outfits")
    fun getAllScheduledOutfits(): Flow<List<ScheduledOutfitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledOutfit: ScheduledOutfitEntity)

    @Delete
    suspend fun delete(scheduledOutfit: ScheduledOutfitEntity)
}
