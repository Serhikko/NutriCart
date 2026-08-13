package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One free-text note per day ("sick", "birthday dinner") — context for why a
 * day looks the way it does in the diary and the adherence calendar.
 */
@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val epochDay: Long,
    val text: String,
    val updatedAtEpochMillis: Long,
)
