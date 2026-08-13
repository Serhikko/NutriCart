package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.WorkoutType
import java.time.DayOfWeek

/**
 * A standing rule: "this workout happens on these weekdays" (e.g. a commute
 * walk Mon-Fri). Once per day the app turns matching rules into ordinary
 * workout_entry rows — deleting such a row later does NOT bring it back
 * (materialization runs once per calendar day).
 */
@Entity(tableName = "recurring_workout")
data class RecurringWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WorkoutType,
    /** Minutes for DURATION types, null for REPS types. */
    val minutes: Int?,
    /** Repetitions for REPS types, null for DURATION types. */
    val reps: Int?,
    /** Which weekdays the rule fires on (CSV via Converters). */
    val days: List<DayOfWeek>,
)
