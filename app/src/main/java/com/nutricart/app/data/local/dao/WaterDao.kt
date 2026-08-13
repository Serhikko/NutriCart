package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutricart.app.data.local.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow

/** Water over a range (the caller averages it per logged diary day). */
data class WaterRangeStats(
    val totalMl: Int,
)

@Dao
interface WaterDao {

    @Query(
        """
        SELECT COALESCE(SUM(ml), 0) AS totalMl
        FROM water_entry WHERE epochDay BETWEEN :from AND :to
        """
    )
    suspend fun rangeStats(from: Long, to: Long): WaterRangeStats

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE epochDay = :epochDay")
    fun observeDayTotal(epochDay: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: WaterEntryEntity)

    /** Undo: removes the most recent entry of the day (a mistaken tap). */
    @Query(
        """
        DELETE FROM water_entry WHERE id = (
            SELECT id FROM water_entry WHERE epochDay = :epochDay
            ORDER BY loggedAtEpochMillis DESC LIMIT 1
        )
        """
    )
    suspend fun removeLast(epochDay: Long)
}
