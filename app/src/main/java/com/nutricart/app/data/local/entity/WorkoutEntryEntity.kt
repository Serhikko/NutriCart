package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.WorkoutSource
import com.nutricart.app.domain.model.WorkoutType

/**
 * One workout on one day: either logged by hand or imported from Health Connect.
 *
 * kcal is a SNAPSHOT taken at log/sync time (like diary entries snapshot their
 * nutrition): changing the weight later must not rewrite past workouts.
 * For HEALTH_CONNECT rows kcal can be null — the watch recorded the session
 * but reported no calories for it; that is "no data", not 0.
 *
 * hcSessionId is the Health Connect record id. The UNIQUE index stops a
 * re-synced session from appearing twice; SQLite ignores NULLs in unique
 * indexes, so MANUAL rows (always null) never collide with each other.
 */
@Entity(
    tableName = "workout_entry",
    indices = [
        Index("epochDay"),
        Index(value = ["hcSessionId"], unique = true),
        // One materialized row per (rule, day) — a race between two refreshes
        // can never duplicate a recurring workout (insert uses IGNORE).
        Index(value = ["recurringId", "epochDay"], unique = true),
    ],
)
data class WorkoutEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val source: WorkoutSource,
    /** The recurring rule that produced this row; null = not from a rule.
     *  Plain column, no FK: deleting a rule keeps its past entries as history. */
    val recurringId: Long? = null,
    /** Health Connect record id; null for MANUAL entries. */
    val hcSessionId: String?,
    /**
     * Raw ExerciseSessionRecord.EXERCISE_TYPE_* constant; null for MANUAL
     * entries. Stored instead of a ready-made name so the UI can localize it.
     */
    val hcExerciseType: Int?,
    /** Catalog type; null for HEALTH_CONNECT rows (they carry a title instead). */
    val type: WorkoutType?,
    /** Watch session name (e.g. "Morning run"); null for MANUAL entries. */
    val title: String?,
    val minutes: Int?,
    val reps: Int?,
    val kcal: Double?,
    val loggedAtEpochMillis: Long,
)
