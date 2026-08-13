package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.DayNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM day_note WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DayNoteEntity?>

    @Upsert
    suspend fun upsert(note: DayNoteEntity)

    @Query("DELETE FROM day_note WHERE epochDay = :epochDay")
    suspend fun deleteByDay(epochDay: Long)
}
