package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import kotlinx.coroutines.flow.Flow

/** Summed nutrition of one diary day (COALESCE turns "no rows" into 0). */
data class DayNutritionTotals(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
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
               COALESCE(SUM(carbsG), 0) AS carbsG
        FROM food_log_entry WHERE epochDay = :epochDay
        """
    )
    fun observeDayTotals(epochDay: Long): Flow<DayNutritionTotals>

    @Insert
    suspend fun insert(entry: FoodLogEntryEntity)

    @Delete
    suspend fun delete(entry: FoodLogEntryEntity)
}
