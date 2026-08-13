package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTargetMathTest {

    private val delta = 0.001

    // A fixed reference person: male, 80 kg, 180 cm, 30 y -> BMR = 1780.
    private fun target(
        customKcal: Int? = null,
        activeKcal: Double? = null,
        manualKcal: Double = 0.0,
        level: ActivityLevel = ActivityLevel.MODERATE,
    ): Double = DailyTargetMath.dayTargetKcal(
        sex = Sex.MALE, weightKg = 80.0, heightCm = 180.0, ageYears = 30,
        level = level, goal = Goal.MAINTAIN, targetKgPerWeek = 0.0,
        customKcalTarget = customKcal, activeKcal = activeKcal,
        manualWorkoutKcal = manualKcal,
    )

    @Test
    fun `no data means the plain base formula`() {
        // BMR 1780 * 1.55 = 2759.
        assertEquals(1780.0 * 1.55, target(), delta)
    }

    @Test
    fun `watch data switches to the adjusted formula`() {
        // BMR * 1.2 + measured 600.
        assertEquals(1780.0 * 1.2 + 600.0, target(activeKcal = 600.0), delta)
    }

    @Test
    fun `manual workouts always add on top`() {
        assertEquals(1780.0 * 1.55 + 250.0, target(manualKcal = 250.0), delta)
        assertEquals(1780.0 * 1.2 + 600.0 + 250.0, target(activeKcal = 600.0, manualKcal = 250.0), delta)
    }

    @Test
    fun `a custom target replaces the base formula`() {
        assertEquals(2200.0, target(customKcal = 2200), delta)
    }

    @Test
    fun `a custom target also disables the watch switch`() {
        // The user's number is authoritative — measured activity changes nothing.
        assertEquals(2200.0, target(customKcal = 2200, activeKcal = 600.0), delta)
    }

    @Test
    fun `manual workouts add even to a custom target`() {
        assertEquals(2200.0 + 250.0, target(customKcal = 2200, manualKcal = 250.0), delta)
    }

    @Test
    fun `reference equals the day target on a plain day`() {
        val reference = DailyTargetMath.referenceKcal(
            Sex.MALE, 80.0, 180.0, 30,
            ActivityLevel.MODERATE, Goal.MAINTAIN, 0.0, customKcalTarget = null,
        )
        assertEquals(reference, target(), delta)
    }
}
