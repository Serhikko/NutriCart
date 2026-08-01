package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached daily totals from Health Connect, one row per day.
 * The dashboard reads THIS table, so it works offline; the sync just refreshes it.
 *
 * EVERY value column is nullable, and null means "Health Connect had no data
 * for this day". That is different from a real 0 (e.g. measured zero active
 * kcal) — the target formula switches to watch-mode only when activeKcal is
 * actually measured, so the difference matters.
 */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int?,
    val activeKcal: Double?,
    val exerciseMinutes: Int?,
    val sleepMinutes: Int?,
    val avgHeartRateBpm: Int?,
)
