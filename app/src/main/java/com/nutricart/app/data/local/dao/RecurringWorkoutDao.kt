package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity

@Dao
interface RecurringWorkoutDao {

    @Query("SELECT * FROM recurring_workout")
    suspend fun all(): List<RecurringWorkoutEntity>

    @Insert
    suspend fun insert(rule: RecurringWorkoutEntity): Long

    @Query("DELETE FROM recurring_workout WHERE id = :id")
    suspend fun delete(id: Long)
}
