package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType

/**
 * Burned-calorie estimates for MANUALLY logged workouts: pure math, no Android,
 * fully covered by plain JUnit tests.
 *
 * The result is a snapshot: the caller computes kcal once at log time and
 * stores it, the same way diary entries snapshot their nutrition.
 */
object WorkoutMath {

    /** The body weight the per-repetition constants were measured for. */
    const val REFERENCE_WEIGHT_KG = 70.0

    /** kcal for a time-based workout: MET * weight * hours. */
    fun kcalForDuration(type: WorkoutType, weightKg: Double, minutes: Int): Double {
        require(type.kind == WorkoutKind.DURATION) {
            "$type is logged in repetitions, not minutes"
        }
        return type.met * weightKg * (minutes / 60.0)
    }

    /** kcal for a repetition-based workout, scaled linearly by body weight. */
    fun kcalForReps(type: WorkoutType, weightKg: Double, reps: Int): Double {
        require(type.kind == WorkoutKind.REPS) {
            "$type is logged in minutes, not repetitions"
        }
        return type.kcalPerRepAt70Kg * reps * (weightKg / REFERENCE_WEIGHT_KG)
    }
}
