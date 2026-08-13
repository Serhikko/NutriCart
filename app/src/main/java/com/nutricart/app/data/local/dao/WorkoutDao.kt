package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nutricart.app.data.local.entity.WorkoutEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // loggedAtEpochMillis is a plain INTEGER, so SQL ORDER BY is safe here
    // (unlike the TEXT enum columns that must be sorted in Kotlin).
    @Query("SELECT * FROM workout_entry WHERE epochDay = :epochDay ORDER BY loggedAtEpochMillis")
    fun observeDay(epochDay: Long): Flow<List<WorkoutEntryEntity>>

    @Insert
    suspend fun insert(entry: WorkoutEntryEntity)

    /**
     * Deletes one entry, but ONLY a manual one: watch sessions would just
     * come back on the next sync, so the UI never offers deleting them.
     * ('MANUAL' matches WorkoutSource.MANUAL — Room stores enums by name.)
     */
    @Query("DELETE FROM workout_entry WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManual(id: Long)

    @Query("DELETE FROM workout_entry WHERE epochDay = :epochDay AND source = 'HEALTH_CONNECT'")
    suspend fun deleteHealthConnectForDay(epochDay: Long)

    /**
     * Manual workout kcal per day over a range — the statistics screen adds
     * these to each day's historical target (watch kcal is inside activeKcal).
     */
    @Query(
        """
        SELECT epochDay, COALESCE(SUM(kcal), 0) AS kcal FROM workout_entry
        WHERE source = 'MANUAL' AND epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        """
    )
    suspend fun manualKcalByDay(from: Long, to: Long): List<DayKcal>

    /**
     * IGNORE + the unique (recurringId, epochDay) index = idempotent
     * materialization of recurring workouts: the second attempt for the same
     * rule and day is silently a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: WorkoutEntryEntity)

    /**
     * REPLACE on purpose: a session's day is recomputed from the CURRENT
     * timezone on every sync, so a stored session can MOVE to another day
     * (travel, or its start edited in the watch app). The default ABORT would
     * hit the unique hcSessionId index and kill the whole sync; REPLACE lets
     * SQLite drop the old-day row and re-insert cleanly. Safe here because no
     * foreign key points at workout_entry (REPLACE is banned only on FK
     * parents — it deletes rows, which would cascade or null out children).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WorkoutEntryEntity>)

    /**
     * Sync writes a day's watch sessions as delete-then-insert in ONE
     * transaction: sessions deleted in Health Connect disappear here too,
     * and a reader never observes the emptied in-between state.
     * Manual rows are untouched — the delete filters on source.
     */
    @Transaction
    suspend fun replaceHealthConnectDay(epochDay: Long, entries: List<WorkoutEntryEntity>) {
        deleteHealthConnectForDay(epochDay)
        insertAll(entries)
    }
}
