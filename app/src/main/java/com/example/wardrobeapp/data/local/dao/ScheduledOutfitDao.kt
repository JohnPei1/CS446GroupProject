package com.example.wardrobeapp.data.local.dao

import androidx.room.*
import com.example.wardrobeapp.data.local.entity.ScheduledOutfitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledOutfitDao {
    @Query("SELECT * FROM scheduled_outfits")
    fun getAllScheduledOutfits(): Flow<List<ScheduledOutfitEntity>>

    @Query("SELECT * FROM scheduled_outfits WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: Long): ScheduledOutfitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledOutfit: ScheduledOutfitEntity)

    @Delete
    suspend fun delete(scheduledOutfit: ScheduledOutfitEntity)

    @Query("DELETE FROM scheduled_outfits WHERE date = :date")
    suspend fun deleteByDate(date: Long)

    @Query("DELETE FROM scheduled_outfits WHERE outfitId = :outfitId")
    suspend fun deleteByOutfitId(outfitId: Long)
}
