package com.example.wardrobeapp.data.local.dao

import androidx.room.*
import com.example.wardrobeapp.data.local.entity.ClothingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingItemDao {
    @Query("SELECT * FROM clothing_items")
    fun getAllItems(): Flow<List<ClothingItemEntity>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun getItem(id: Long): Flow<ClothingItemEntity?>

    @Query("SELECT * FROM clothing_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ClothingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClothingItemEntity)

    @Update
    suspend fun update(item: ClothingItemEntity)

    @Delete
    suspend fun delete(item: ClothingItemEntity)

    @Query("UPDATE clothing_items SET timesWorn = timesWorn + 1, lastWornDate = :wornAt WHERE id IN (:ids)")
    suspend fun markWorn(ids: List<Long>, wornAt: Long)
}
