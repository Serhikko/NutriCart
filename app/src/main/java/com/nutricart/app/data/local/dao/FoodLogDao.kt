package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import kotlinx.coroutines.flow.Flow

/** Eaten kcal of one day — a bar of the statistics chart. */
data class DayKcal(
    val epochDay: Long,
    val kcal: Double,
)

/** Range sums + how many days actually have entries (for honest averages). */
data class RangeNutritionTotals(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
    val sugarsG: Double,
    val saltG: Double,
    val saturatedFatG: Double,
    val loggedDays: Int,
)

/** One food and how much of a nutrient it contributed over a range. */
data class FoodContribution(
    val name: String,
    val amount: Double,
)

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

    /** Is this meal already logged today? — the reminder stays silent if so. */
    @Query("SELECT COUNT(*) FROM food_log_entry WHERE epochDay = :epochDay AND meal = :meal")
    suspend fun countForSlot(epochDay: Long, meal: com.nutricart.app.domain.model.MealSlot): Int

    // --- Statistics range queries (v0.12) ---

    @Query(
        """
        SELECT epochDay, SUM(kcal) AS kcal FROM food_log_entry
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        """
    )
    suspend fun dayKcalBetween(from: Long, to: Long): List<DayKcal>

    @Query(
        """
        SELECT COALESCE(SUM(kcal), 0) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(fatG), 0) AS fatG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fiberG), 0) AS fiberG,
               COALESCE(SUM(sugarsG), 0) AS sugarsG,
               COALESCE(SUM(saltG), 0) AS saltG,
               COALESCE(SUM(saturatedFatG), 0) AS saturatedFatG,
               COUNT(DISTINCT epochDay) AS loggedDays
        FROM food_log_entry WHERE epochDay BETWEEN :from AND :to
        """
    )
    suspend fun rangeTotals(from: Long, to: Long): RangeNutritionTotals

    // Four almost-identical queries instead of one clever parameterized SQL:
    // a column name cannot be a bind parameter, and copy-paste beats string
    // concatenation into raw queries. HAVING > 0 drops all-null foods.
    @Query(
        """
        SELECT name, SUM(proteinG) AS amount FROM food_log_entry
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY name HAVING amount > 0 ORDER BY amount DESC LIMIT 5
        """
    )
    suspend fun topProteinSources(from: Long, to: Long): List<FoodContribution>

    @Query(
        """
        SELECT name, SUM(fatG) AS amount FROM food_log_entry
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY name HAVING amount > 0 ORDER BY amount DESC LIMIT 5
        """
    )
    suspend fun topFatSources(from: Long, to: Long): List<FoodContribution>

    @Query(
        """
        SELECT name, SUM(carbsG) AS amount FROM food_log_entry
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY name HAVING amount > 0 ORDER BY amount DESC LIMIT 5
        """
    )
    suspend fun topCarbSources(from: Long, to: Long): List<FoodContribution>

    @Query(
        """
        SELECT name, SUM(sugarsG) AS amount FROM food_log_entry
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY name HAVING amount > 0 ORDER BY amount DESC LIMIT 5
        """
    )
    suspend fun topSugarSources(from: Long, to: Long): List<FoodContribution>

    /** Returns the new row id — the cloud mirror needs it for the row's remote id. */
    @Insert
    suspend fun insert(entry: FoodLogEntryEntity): Long

    /**
     * Room wraps a list insert in ONE transaction — a multi-item write (saved
     * meal, basket) lands complete or not at all, never half a meal.
     * Returns the new ids in input order.
     */
    @Insert
    suspend fun insertAll(entries: List<FoodLogEntryEntity>): List<Long>

    /** Every entry in a day range — the cloud backfill when sync is switched on. */
    @Query("SELECT * FROM food_log_entry WHERE epochDay BETWEEN :from AND :to ORDER BY id")
    suspend fun entriesBetween(from: Long, to: Long): List<FoodLogEntryEntity>

    @Delete
    suspend fun delete(entry: FoodLogEntryEntity)

    // --- Cloud pull (v13 -> v14): rows another client wrote, keyed by their cloud id ---

    @Query("SELECT * FROM food_log_entry WHERE cloudId = :cloudId LIMIT 1")
    suspend fun byCloudId(cloudId: String): FoodLogEntryEntity?

    @Query("SELECT * FROM food_log_entry WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): FoodLogEntryEntity?

    @Update
    suspend fun update(entry: FoodLogEntryEntity)

    @Query("DELETE FROM food_log_entry WHERE cloudId = :cloudId")
    suspend fun deleteByCloudId(cloudId: String)

    @Query("DELETE FROM food_log_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
