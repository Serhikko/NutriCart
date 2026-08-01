package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One tap of the water quick-add buttons; the dashboard shows the day's sum. */
@Entity(
    tableName = "water_entry",
    indices = [Index("epochDay")],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val ml: Int,
    val loggedAtEpochMillis: Long,
)
