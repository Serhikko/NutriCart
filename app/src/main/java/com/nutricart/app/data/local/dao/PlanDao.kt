package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nutricart.app.data.local.entity.PlannedMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM planned_meal WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<PlannedMealEntity>>

    @Query("SELECT * FROM planned_meal WHERE id = :id")
    suspend fun byId(id: Long): PlannedMealEntity?

    @Query("SELECT * FROM planned_meal WHERE epochDay = :epochDay AND isLocked = 1")
    suspend fun lockedOnDay(epochDay: Long): List<PlannedMealEntity>

    /** The meals of the user-selected days — input for the shopping list. */
    @Query("SELECT * FROM planned_meal WHERE epochDay IN (:days)")
    suspend fun onDays(days: List<Long>): List<PlannedMealEntity>

    @Insert
    suspend fun insertAll(meals: List<PlannedMealEntity>)

    @Query("DELETE FROM planned_meal WHERE epochDay BETWEEN :startDay AND :endDay AND isLocked = 0")
    suspend fun deleteUnlockedRange(startDay: Long, endDay: Long)

    /** One transaction: the old free meals disappear and the new ones appear together. */
    @Transaction
    suspend fun replaceWeek(startDay: Long, endDay: Long, meals: List<PlannedMealEntity>) {
        deleteUnlockedRange(startDay, endDay)
        insertAll(meals)
    }

    @Query("UPDATE planned_meal SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE planned_meal SET recipeId = :recipeId, portionFactor = :factor WHERE id = :id")
    suspend fun replaceRecipe(id: Long, recipeId: Long, factor: Double)
}
