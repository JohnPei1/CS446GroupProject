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

    // Undoes markWorn -- floors at 0, and clears lastWornDate once the count reaches 0 rather
    // than leaving a stale "last worn" date with nothing behind it.
    @Query(
        """
        UPDATE clothing_items
        SET timesWorn = MAX(timesWorn - 1, 0),
            lastWornDate = CASE WHEN timesWorn - 1 <= 0 THEN NULL ELSE lastWornDate END
        WHERE id IN (:ids)
        """
    )
    suspend fun unmarkWorn(ids: List<Long>)

    // One-time cleanup for wear stats left stale by schedule/unschedule cycles that predate the
    // unmarkWorn fix -- only touches these two columns, every other field on every row (name,
    // category, photo, tags, everything) is untouched, and no rows are inserted or deleted.
    @Query("UPDATE clothing_items SET timesWorn = 0, lastWornDate = NULL")
    suspend fun resetWornStats()
}
