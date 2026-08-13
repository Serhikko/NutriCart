package com.nutricart.app.ui.common

import androidx.annotation.StringRes
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nutricart.app.R
import com.nutricart.app.domain.model.WorkoutType

/**
 * Maps workout identifiers to string resources. Names are resolved at DISPLAY
 * time (not stored in the database) so they always follow the app language.
 */

@StringRes
fun workoutTypeLabel(type: WorkoutType): Int = when (type) {
    WorkoutType.RUNNING -> R.string.workout_running
    WorkoutType.TREADMILL_WALK -> R.string.workout_treadmill_walk
    WorkoutType.CYCLING -> R.string.workout_cycling
    WorkoutType.SWIMMING -> R.string.workout_swimming
    WorkoutType.STRENGTH_TRAINING -> R.string.workout_strength
    WorkoutType.JUMP_ROPE -> R.string.workout_jump_rope
    WorkoutType.FOOTBALL -> R.string.workout_football
    WorkoutType.YOGA -> R.string.workout_yoga
    WorkoutType.OTHER -> R.string.workout_other
    WorkoutType.PUSH_UPS -> R.string.workout_push_ups
    WorkoutType.SQUATS -> R.string.workout_squats
    WorkoutType.PULL_UPS -> R.string.workout_pull_ups
    WorkoutType.SIT_UPS -> R.string.workout_sit_ups
}

/**
 * Fallback name for a watch session that came without its own title.
 * Only the common exercise types are mapped; everything else is "Workout".
 */
@StringRes
fun hcExerciseLabel(exerciseType: Int?): Int = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> R.string.workout_running

    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> R.string.workout_walking
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.workout_hiking

    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> R.string.workout_cycling

    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> R.string.workout_swimming

    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
    -> R.string.workout_strength

    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING ->
        R.string.workout_hiit

    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> R.string.workout_football
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> R.string.workout_basketball
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> R.string.workout_yoga

    else -> R.string.workout_generic
}
