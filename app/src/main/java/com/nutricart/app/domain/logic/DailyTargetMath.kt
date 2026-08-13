package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex

/**
 * The ONE rule for "what is the kcal target of a given day", shared by the
 * dashboard (today) and the statistics screen (past days). Pure Kotlin.
 *
 * Rules (all approved earlier):
 *  - a manual override replaces BOTH formulas; the watch switches nothing;
 *  - without an override, a day with measured activeKcal uses the watch
 *    formula (BMR * 1.2 + activeKcal + delta), otherwise the base formula;
 *  - manually logged workouts ALWAYS add on top.
 *
 * For past days the statistics screen feeds the CURRENT profile and weight —
 * an approved approximation: historical targets were never stored.
 */
object DailyTargetMath {

    /** The no-activity reference: manual override or the base formula. */
    fun referenceKcal(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        level: ActivityLevel,
        goal: Goal,
        targetKgPerWeek: Double,
        customKcalTarget: Int?,
    ): Double = customKcalTarget?.toDouble() ?: CalorieCalculator.baseTargetKcal(
        sex, weightKg, heightCm, ageYears, level, goal, targetKgPerWeek,
    )

    /** The full day target: reference or watch-adjusted, plus manual workouts. */
    fun dayTargetKcal(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        level: ActivityLevel,
        goal: Goal,
        targetKgPerWeek: Double,
        customKcalTarget: Int?,
        activeKcal: Double?,
        manualWorkoutKcal: Double,
    ): Double {
        val reference = referenceKcal(
            sex, weightKg, heightCm, ageYears, level, goal, targetKgPerWeek, customKcalTarget,
        )
        return manualWorkoutKcal + if (customKcalTarget == null && activeKcal != null) {
            CalorieCalculator.adjustedTargetKcal(
                sex,
                CalorieCalculator.bmr(sex, weightKg, heightCm, ageYears),
                goal,
                targetKgPerWeek,
                activeKcal,
            )
        } else {
            reference
        }
    }
}
