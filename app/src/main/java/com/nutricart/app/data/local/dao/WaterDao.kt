package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
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
    suspend fun insert(entry: WaterEntryEntity): Long

    /** The most recent entry of the day — what "undo" removes. */
    @Query(
        """
        SELECT * FROM water_entry WHERE epochDay = :epochDay
        ORDER BY loggedAtEpochMillis DESC LIMIT 1
        """
    )
    suspend fun last(epochDay: Long): WaterEntryEntity?

    @Delete
    suspend fun delete(entry: WaterEntryEntity)

    /** Every entry in a day range — the cloud backfill when sync is switched on. */
    @Query("SELECT * FROM water_entry WHERE epochDay BETWEEN :from AND :to ORDER BY id")
    suspend fun entriesBetween(from: Long, to: Long): List<WaterEntryEntity>
}
