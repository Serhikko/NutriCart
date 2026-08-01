package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.DailyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query("SELECT * FROM daily_activity WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DailyActivityEntity?>

    @Upsert
    suspend fun upsert(day: DailyActivityEntity)
}
