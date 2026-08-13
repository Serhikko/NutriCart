package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Summed nutrition of one diary day (COALESCE turns "no rows" into 0).
 * Detail nutrients sum only the entries that KNOW their value (SQL SUM
 * skips nulls) — an unknown label never becomes a fake 0 in the total.
 */
data class DayNutritionTotals(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double = 0.0,
    val sugarsG: Double = 0.0,
    val saltG: Double = 0.0,
    val saturatedFatG: Double = 0.0,
)

@Dao
interface FoodLogDao {

    @Query("SELECT * FROM food_log_entry WHERE epochDay = :epochDay ORDER BY loggedAtEpochMillis")
    fun observeDay(epochDay: Long): Flow<List<FoodLogEntryEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(kcal), 0) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(fatG), 0) AS fatG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fiberG), 0) AS fiberG,
               COALESCE(SUM(sugarsG), 0) AS sugarsG,
               COALESCE(SUM(saltG), 0) AS saltG,
               COALESCE(SUM(saturatedFatG), 0) AS saturatedFatG
        FROM food_log_entry WHERE epochDay = :epochDay
        """
    )
    fun observeDayTotals(epochDay: Long): Flow<DayNutritionTotals>

    /** Which days have at least one entry — feeds the dashboard streak. */
    @Query("SELECT DISTINCT epochDay FROM food_log_entry")
    fun observeLoggedDays(): Flow<List<Long>>

    @Insert
    suspend fun insert(entry: FoodLogEntryEntity)

    /**
     * Room wraps a list insert in ONE transaction — a multi-item write (saved
     * meal, basket) lands complete or not at all, never half a meal.
     */
    @Insert
    suspend fun insertAll(entries: List<FoodLogEntryEntity>)

    @Delete
    suspend fun delete(entry: FoodLogEntryEntity)
}
