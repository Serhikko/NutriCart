package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nutricart.app.data.local.entity.SavedMealEntity
import com.nutricart.app.data.local.entity.SavedMealItemEntity

/** One row of the saved-meals picker: the name plus what it adds up to. */
data class SavedMealSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val totalKcal: Double,
)

@Dao
interface SavedMealDao {

    @Query(
        """
        SELECT m.id AS id, m.name AS name,
               COUNT(i.id) AS itemCount,
               COALESCE(SUM(i.kcal), 0) AS totalKcal
        FROM saved_meal m
        LEFT JOIN saved_meal_item i ON i.mealId = m.id
        GROUP BY m.id
        ORDER BY m.name
        """
    )
    suspend fun summaries(): List<SavedMealSummary>

    @Query("SELECT * FROM saved_meal_item WHERE mealId = :mealId")
    suspend fun itemsFor(mealId: Long): List<SavedMealItemEntity>

    @Insert
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    /** The meal and its items land together or not at all. */
    @Transaction
    suspend fun insertMealWithItems(meal: SavedMealEntity, items: List<SavedMealItemEntity>) {
        val mealId = insertMeal(meal)
        insertItems(items.map { it.copy(mealId = mealId) })
    }

    // Items go with it automatically — the foreign key cascades.
    @Query("DELETE FROM saved_meal WHERE id = :id")
    suspend fun deleteMeal(id: Long)
}
