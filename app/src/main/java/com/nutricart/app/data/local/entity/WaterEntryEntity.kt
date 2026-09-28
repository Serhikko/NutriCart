package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One tap of the water quick-add buttons; the dashboard shows the day's sum. */
@Entity(
    tableName = "water_entry",
    indices = [Index("epochDay"), Index(value = ["cloudId"], unique = true)],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val ml: Int,
    val loggedAtEpochMillis: Long,
    /** Set only for glasses another client logged; see FoodLogEntryEntity.cloudId. */
    val cloudId: String? = null,
)
