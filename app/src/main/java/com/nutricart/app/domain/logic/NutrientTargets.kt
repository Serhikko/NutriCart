package com.nutricart.app.domain.logic

import kotlin.math.roundToInt

/**
 * Daily guides for the detail nutrients, derived from the day's kcal target —
 * no magic numbers in the UI. Pure Kotlin, JUnit-tested.
 *
 * Sources of the constants:
 *  - fiber: 14 g per 1000 kcal (Academy of Nutrition and Dietetics),
 *  - free sugars: at most 10% of energy (WHO),
 *  - saturated fat: at most 10% of energy (WHO),
 *  - salt: at most 5 g/day (WHO), independent of energy.
 *
 * Two kinds of nutrients, two kinds of feedback:
 *  - TARGET nutrients (protein, fat, carbs, fiber) are something to REACH:
 *    neutral while below, green in the 90–110% band, "over" above it.
 *  - LIMIT nutrients (sugar, salt, saturated fat) are something to STAY UNDER:
 *    green up to the limit, warning up to 120%, "over" beyond.
 */
object NutrientTargets {

    const val FIBER_G_PER_1000_KCAL = 14.0
    const val SUGAR_SHARE_OF_KCAL = 0.10
    const val SAT_FAT_SHARE_OF_KCAL = 0.10
    const val SALT_LIMIT_G = 5.0

    private const val KCAL_PER_G_SUGAR = 4.0 // sugar is a carbohydrate
    private const val KCAL_PER_G_FAT = 9.0

    fun fiberTargetG(kcalTarget: Int): Int =
        (kcalTarget * FIBER_G_PER_1000_KCAL / 1000.0).roundToInt()

    fun sugarLimitG(kcalTarget: Int): Int =
        (kcalTarget * SUGAR_SHARE_OF_KCAL / KCAL_PER_G_SUGAR).roundToInt()

    fun saturatedFatLimitG(kcalTarget: Int): Int =
        (kcalTarget * SAT_FAT_SHARE_OF_KCAL / KCAL_PER_G_FAT).roundToInt()

    enum class State { NEUTRAL, GOOD, WARN, OVER }

    /** Feedback for a nutrient with a target to REACH (protein, fat, carbs, fiber). */
    fun targetState(consumedG: Double, targetG: Int): State {
        if (targetG <= 0) return State.NEUTRAL // no sane target -> no verdict
        val ratio = consumedG / targetG
        return when {
            ratio < 0.9 -> State.NEUTRAL // still on the way, nothing to judge
            ratio <= 1.1 -> State.GOOD
            else -> State.OVER
        }
    }

    /** Feedback for a nutrient with a limit to STAY UNDER (sugar, salt, sat. fat). */
    fun limitState(consumedG: Double, limitG: Double): State {
        if (limitG <= 0.0) return State.NEUTRAL
        val ratio = consumedG / limitG
        return when {
            ratio <= 1.0 -> State.GOOD
            ratio <= 1.2 -> State.WARN
            else -> State.OVER
        }
    }
}
