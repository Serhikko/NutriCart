package com.nutricart.app.domain.model

/** How the amount of a workout is entered: by time or by repetition count. */
enum class WorkoutKind { DURATION, REPS }

/**
 * The catalog of MANUALLY loggable workouts.
 *
 * DURATION workouts carry a MET value ("metabolic equivalent of task", from the
 * Compendium of Physical Activities): kcal = MET * weight(kg) * hours.
 * REPS workouts carry a per-repetition kcal estimate measured for a 70 kg
 * person; WorkoutMath scales it linearly by the user's real weight.
 *
 * Watch-recorded sessions do NOT use this catalog — their calories arrive
 * already measured from Health Connect.
 */
enum class WorkoutType(
    val kind: WorkoutKind,
    /** Meaningful only when kind == DURATION. */
    val met: Double = 0.0,
    /** Meaningful only when kind == REPS. */
    val kcalPerRepAt70Kg: Double = 0.0,
) {
    RUNNING(WorkoutKind.DURATION, met = 9.8),
    TREADMILL_WALK(WorkoutKind.DURATION, met = 4.3),
    CYCLING(WorkoutKind.DURATION, met = 7.5),
    SWIMMING(WorkoutKind.DURATION, met = 7.0),
    STRENGTH_TRAINING(WorkoutKind.DURATION, met = 5.0),
    JUMP_ROPE(WorkoutKind.DURATION, met = 11.0),
    FOOTBALL(WorkoutKind.DURATION, met = 7.0),
    YOGA(WorkoutKind.DURATION, met = 3.0),
    OTHER(WorkoutKind.DURATION, met = 5.0),

    PUSH_UPS(WorkoutKind.REPS, kcalPerRepAt70Kg = 0.36),
    SQUATS(WorkoutKind.REPS, kcalPerRepAt70Kg = 0.32),
    PULL_UPS(WorkoutKind.REPS, kcalPerRepAt70Kg = 1.0),
    SIT_UPS(WorkoutKind.REPS, kcalPerRepAt70Kg = 0.25),
}
