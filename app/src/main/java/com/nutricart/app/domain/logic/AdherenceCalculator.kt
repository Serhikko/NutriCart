package com.nutricart.app.domain.logic

/**
 * Verdict of one calendar day for the adherence calendar and the "days on
 * plan" counters. Pure Kotlin, JUnit-tested.
 */
object AdherenceCalculator {

    /** A small overshoot is life, not failure. */
    const val TOLERANCE = 0.05

    enum class DayState { EMPTY, GOOD, OVER }

    /**
     * EMPTY = nothing logged (or no sane target — an empty verdict beats a
     * false one), GOOD = eaten within target + 5%, OVER = above that.
     */
    fun dayState(eatenKcal: Double?, targetKcal: Double): DayState = when {
        eatenKcal == null || eatenKcal <= 0.0 -> DayState.EMPTY
        targetKcal <= 0.0 -> DayState.EMPTY
        eatenKcal <= targetKcal * (1 + TOLERANCE) -> DayState.GOOD
        else -> DayState.OVER
    }
}
