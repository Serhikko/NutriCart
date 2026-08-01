package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex
import java.time.LocalDate
import java.time.Period
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All calorie and macro math in one pure object: no Android classes, no database,
 * no clock — every function's output depends only on its inputs, so plain JUnit
 * tests can verify every rule.
 */
object CalorieCalculator {

    /** Energy in roughly 1 kg of body fat — used to convert "kg per week" into "kcal per day". */
    const val KCAL_PER_KG_BODY_FAT = 7700.0

    /** Hard safety floors: the app never sets a target below these, whatever the goal says. */
    const val MIN_KCAL_MALE = 1500.0
    const val MIN_KCAL_FEMALE = 1200.0

    const val ADULT_AGE_YEARS = 18

    private const val PROTEIN_G_PER_KG = 1.8
    private const val FAT_SHARE_OF_KCAL = 0.25
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_CARB = 4.0

    /** Baseline for non-exercise daily living (walking around the house, cooking, etc.). */
    private const val SEDENTARY_MULTIPLIER = 1.2

    /** Mifflin-St Jeor: calories the body burns at complete rest. */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val sexTerm = if (sex == Sex.MALE) 5.0 else -161.0
        return 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + sexTerm
    }

    /** Total daily burn estimated from the questionnaire (no watch involved). */
    fun tdee(bmrKcal: Double, level: ActivityLevel): Double = bmrKcal * level.multiplier

    /**
     * How many kcal/day to add or remove to move weight at the requested pace.
     * Example: lose 0.5 kg/week -> -(0.5 * 7700 / 7) = -550 kcal/day.
     */
    fun goalDeltaKcal(goal: Goal, targetKgPerWeek: Double): Double = when (goal) {
        Goal.LOSE -> -targetKgPerWeek * KCAL_PER_KG_BODY_FAT / 7.0
        Goal.MAINTAIN -> 0.0
        Goal.GAIN -> targetKgPerWeek * KCAL_PER_KG_BODY_FAT / 7.0
    }

    fun safetyFloorKcal(sex: Sex): Double =
        if (sex == Sex.MALE) MIN_KCAL_MALE else MIN_KCAL_FEMALE

    /**
     * The BASE daily target: questionnaire only, no watch data.
     * The meal-plan generator always uses this one, because plans are made for
     * future days whose activity is unknown yet.
     */
    fun baseTargetKcal(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        level: ActivityLevel,
        goal: Goal,
        targetKgPerWeek: Double,
    ): Double {
        val raw = tdee(bmr(sex, weightKg, heightCm, ageYears), level) +
            goalDeltaKcal(goal, targetKgPerWeek)
        return max(raw, safetyFloorKcal(sex))
    }

    /**
     * The ADJUSTED daily target for a day where the watch reported active calories.
     *
     * Formula: BMR * 1.2 + activeKcal + goal delta.
     *
     * Why BMR * 1.2 and not BMR * activityLevel: the questionnaire multiplier
     * (1.375...1.9) already CONTAINS exercise. Adding the watch's measured
     * ActiveCaloriesBurned on top of it would count the same workouts twice.
     * So when real measurements exist, we drop the questionnaire's exercise guess
     * (keep only the sedentary baseline 1.2) and use the measured number instead.
     * This is the same double-counting trap as adding TotalCaloriesBurned to BMR.
     */
    fun adjustedTargetKcal(
        sex: Sex,
        bmrKcal: Double,
        goal: Goal,
        targetKgPerWeek: Double,
        activeKcal: Double,
    ): Double {
        val raw = bmrKcal * SEDENTARY_MULTIPLIER + activeKcal +
            goalDeltaKcal(goal, targetKgPerWeek)
        return max(raw, safetyFloorKcal(sex))
    }

    /**
     * "Calories out" for the dashboard.
     * With watch data: sedentary baseline + measured active calories.
     * Without: fall back to the questionnaire estimate (TDEE).
     */
    fun caloriesOut(bmrKcal: Double, level: ActivityLevel, activeKcal: Double?): Double =
        if (activeKcal != null) {
            bmrKcal * SEDENTARY_MULTIPLIER + activeKcal
        } else {
            tdee(bmrKcal, level)
        }

    /**
     * Macro split, priority order protein -> fat -> carbs:
     *  - protein: 1.8 g per kg of body weight,
     *  - fat: 25% of the kcal target,
     *  - carbs: whatever kcal remain.
     *
     * Protein is CAPPED so protein + fat never exceed the kcal budget. Without the
     * cap, a heavy user clamped to the safety floor would get negative carbs
     * (e.g. 130 kg at 1200 kcal: 234 g protein = 936 kcal + 300 kcal fat > 1200).
     */
    fun macroTargets(kcalTarget: Double, weightKg: Double): DailyTargets {
        val fatKcal = kcalTarget * FAT_SHARE_OF_KCAL
        val fatG = fatKcal / KCAL_PER_G_FAT
        val proteinG = min(
            PROTEIN_G_PER_KG * weightKg,
            (kcalTarget - fatKcal) / KCAL_PER_G_PROTEIN,
        )
        val carbsG = max(
            0.0,
            (kcalTarget - fatKcal - proteinG * KCAL_PER_G_PROTEIN) / KCAL_PER_G_CARB,
        )
        return DailyTargets(
            kcal = kcalTarget.roundToInt(),
            proteinG = proteinG.roundToInt(),
            fatG = fatG.roundToInt(),
            carbsG = carbsG.roundToInt(),
        )
    }

    /** Calendar-correct age ("today" is a parameter so tests don't depend on the real clock). */
    fun ageYears(birthDate: LocalDate, today: LocalDate): Int =
        Period.between(birthDate, today).years

    fun isAdult(birthDate: LocalDate, today: LocalDate): Boolean =
        ageYears(birthDate, today) >= ADULT_AGE_YEARS
}
