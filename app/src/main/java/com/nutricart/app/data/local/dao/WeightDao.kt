package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutricart.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    /**
     * Latest known weight. For the same day a MANUAL entry beats a HEALTH_CONNECT
     * one (the CASE turns the source into a sort rank: MANUAL = 0, watch = 1).
     */
    @Query(
        """
        SELECT * FROM weight_entry
        ORDER BY epochDay DESC,
                 CASE source WHEN 'MANUAL' THEN 0 ELSE 1 END ASC
        LIMIT 1
        """
    )
    fun observeLatest(): Flow<WeightEntryEntity?>

    /**
     * All entries, oldest first — for the weekly chart later.
     * NOTE for the chart step: a day can hold both a MANUAL and a
     * HEALTH_CONNECT row; consumers must keep one point per day,
     * preferring MANUAL (same rule as [observeLatest]).
     */
    @Query("SELECT * FROM weight_entry ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    // REPLACE is safe here because nothing references weight_entry by id.
    // (On tables that other tables point to, REPLACE would be a bug: it works as
    // delete+insert and would break foreign keys — those tables must use @Upsert.)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntryEntity)
}
