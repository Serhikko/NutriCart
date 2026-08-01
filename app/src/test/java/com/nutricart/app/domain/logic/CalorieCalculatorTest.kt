package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Every rule of the calorie math, checked against hand-computed numbers.
 * Reference person used in most tests: male, 80 kg, 180 cm, 25 years.
 * BMR = 10*80 + 6.25*180 - 5*25 + 5 = 1805.
 */
class CalorieCalculatorTest {

    private val delta = 0.01

    // ---------- BMR (Mifflin-St Jeor) ----------

    @Test
    fun `bmr for male matches hand-computed value`() {
        val bmr = CalorieCalculator.bmr(Sex.MALE, weightKg = 80.0, heightCm = 180.0, ageYears = 25)
        assertEquals(1805.0, bmr, delta)
    }

    @Test
    fun `bmr for female is 166 kcal lower than male`() {
        // Same body, sex term changes from +5 to -161.
        val bmr = CalorieCalculator.bmr(Sex.FEMALE, weightKg = 80.0, heightCm = 180.0, ageYears = 25)
        assertEquals(1639.0, bmr, delta)
    }

    // ---------- TDEE ----------

    @Test
    fun `tdee multiplies bmr by the activity multiplier`() {
        assertEquals(2166.0, CalorieCalculator.tdee(1805.0, ActivityLevel.SEDENTARY), delta)
        assertEquals(2797.75, CalorieCalculator.tdee(1805.0, ActivityLevel.MODERATE), delta)
    }

    // ---------- Goal delta ----------

    @Test
    fun `losing half a kg per week means minus 550 kcal per day`() {
        assertEquals(-550.0, CalorieCalculator.goalDeltaKcal(Goal.LOSE, 0.5), delta)
    }

    @Test
    fun `gaining half a kg per week means plus 550 kcal per day`() {
        assertEquals(550.0, CalorieCalculator.goalDeltaKcal(Goal.GAIN, 0.5), delta)
    }

    @Test
    fun `maintaining ignores the rate`() {
        assertEquals(0.0, CalorieCalculator.goalDeltaKcal(Goal.MAINTAIN, 1.0), delta)
    }

    // ---------- Base target + safety floors ----------

    @Test
    fun `base target is tdee plus delta when above the floor`() {
        val target = CalorieCalculator.baseTargetKcal(
            Sex.MALE, 80.0, 180.0, 25, ActivityLevel.MODERATE, Goal.LOSE, 0.5,
        )
        assertEquals(2797.75 - 550.0, target, delta)
    }

    @Test
    fun `female target never drops below 1200`() {
        // Small sedentary woman with an aggressive 1 kg per week goal:
        // BMR = 450 + 968.75 - 150 - 161 = 1107.75; TDEE = 1329.3; -1100 => 229.3 -> floor.
        val target = CalorieCalculator.baseTargetKcal(
            Sex.FEMALE, 45.0, 155.0, 30, ActivityLevel.SEDENTARY, Goal.LOSE, 1.0,
        )
        assertEquals(CalorieCalculator.MIN_KCAL_FEMALE, target, delta)
    }

    @Test
    fun `male target never drops below 1500`() {
        val target = CalorieCalculator.baseTargetKcal(
            Sex.MALE, 60.0, 165.0, 40, ActivityLevel.SEDENTARY, Goal.LOSE, 1.0,
        )
        assertEquals(CalorieCalculator.MIN_KCAL_MALE, target, delta)
    }

    // ---------- Adjusted target (watch data present) ----------

    @Test
    fun `adjusted target uses sedentary baseline plus measured active kcal`() {
        // 1805 * 1.2 + 400 - 550 = 2016. NOT 1805 * activityLevel + 400 (double count).
        val target = CalorieCalculator.adjustedTargetKcal(
            Sex.MALE, bmrKcal = 1805.0, goal = Goal.LOSE, targetKgPerWeek = 0.5, activeKcal = 400.0,
        )
        assertEquals(2016.0, target, delta)
    }

    @Test
    fun `adjusted target also respects the safety floor`() {
        val target = CalorieCalculator.adjustedTargetKcal(
            Sex.FEMALE, bmrKcal = 1107.75, goal = Goal.LOSE, targetKgPerWeek = 1.0, activeKcal = 0.0,
        )
        assertEquals(CalorieCalculator.MIN_KCAL_FEMALE, target, delta)
    }

    // ---------- Calories out ----------

    @Test
    fun `calories out prefers watch data when available`() {
        val out = CalorieCalculator.caloriesOut(1805.0, ActivityLevel.MODERATE, activeKcal = 400.0)
        assertEquals(1805.0 * 1.2 + 400.0, out, delta)
    }

    @Test
    fun `calories out falls back to tdee without watch data`() {
        val out = CalorieCalculator.caloriesOut(1805.0, ActivityLevel.MODERATE, activeKcal = null)
        assertEquals(2797.75, out, delta)
    }

    // ---------- Macros ----------

    @Test
    fun `macros for a normal case`() {
        // 2000 kcal, 80 kg: fat = 500 kcal = 55.6 g; protein = 144 g (1.8 * 80);
        // carbs = (2000 - 500 - 576) / 4 = 231 g.
        val targets = CalorieCalculator.macroTargets(kcalTarget = 2000.0, weightKg = 80.0)
        assertEquals(2000, targets.kcal)
        assertEquals(144, targets.proteinG)
        assertEquals(56, targets.fatG)
        assertEquals(231, targets.carbsG)
    }

    @Test
    fun `macros never go negative for a heavy user at the safety floor`() {
        // 130 kg at 1200 kcal: uncapped protein would be 234 g = 936 kcal,
        // which together with 300 kcal of fat exceeds the whole budget.
        val targets = CalorieCalculator.macroTargets(kcalTarget = 1200.0, weightKg = 130.0)
        assertEquals(225, targets.proteinG) // capped: (1200 - 300) / 4
        assertEquals(33, targets.fatG)
        assertEquals(0, targets.carbsG)     // clamped at zero, never negative
        assertTrue(targets.proteinG >= 0 && targets.fatG >= 0 && targets.carbsG >= 0)
    }

    // ---------- Age / adult check ----------

    @Test
    fun `seventeen year old is blocked even one day before the birthday`() {
        val birthDate = LocalDate.of(2008, 8, 2)
        val today = LocalDate.of(2026, 8, 1)
        assertEquals(17, CalorieCalculator.ageYears(birthDate, today))
        assertFalse(CalorieCalculator.isAdult(birthDate, today))
    }

    @Test
    fun `eighteenth birthday unlocks the app`() {
        val birthDate = LocalDate.of(2008, 8, 1)
        val today = LocalDate.of(2026, 8, 1)
        assertEquals(18, CalorieCalculator.ageYears(birthDate, today))
        assertTrue(CalorieCalculator.isAdult(birthDate, today))
    }
}
