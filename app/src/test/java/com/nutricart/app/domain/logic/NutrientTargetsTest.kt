package com.nutricart.app.domain.logic

import com.nutricart.app.domain.logic.NutrientTargets.State
import org.junit.Assert.assertEquals
import org.junit.Test

class NutrientTargetsTest {

    // --- derived daily guides ---

    @Test
    fun `2000 kcal gives the textbook guides`() {
        // 14 g / 1000 kcal -> 28 g fiber; 10% of energy -> 50 g sugar (WHO's
        // well-known "about 50 g" number) and 22 g saturated fat.
        assertEquals(28, NutrientTargets.fiberTargetG(2000))
        assertEquals(50, NutrientTargets.sugarLimitG(2000))
        assertEquals(22, NutrientTargets.saturatedFatLimitG(2000))
    }

    @Test
    fun `guides scale with the kcal target`() {
        assertEquals(21, NutrientTargets.fiberTargetG(1500))
        assertEquals(38, NutrientTargets.sugarLimitG(1500))
        assertEquals(17, NutrientTargets.saturatedFatLimitG(1500))
    }

    // --- target nutrients: reach the number ---

    @Test
    fun `below 90 percent of a target is neutral`() {
        assertEquals(State.NEUTRAL, NutrientTargets.targetState(25.0, 28))
    }

    @Test
    fun `the 90 to 110 percent band is good`() {
        assertEquals(State.GOOD, NutrientTargets.targetState(25.2, 28)) // exactly 90%
        assertEquals(State.GOOD, NutrientTargets.targetState(28.0, 28))
        assertEquals(State.GOOD, NutrientTargets.targetState(30.8, 28)) // exactly 110%
    }

    @Test
    fun `above 110 percent of a target is over`() {
        assertEquals(State.OVER, NutrientTargets.targetState(31.0, 28))
    }

    @Test
    fun `a zero target gives no verdict`() {
        assertEquals(State.NEUTRAL, NutrientTargets.targetState(10.0, 0))
    }

    // --- limit nutrients: stay under the number ---

    @Test
    fun `at or under a limit is good`() {
        assertEquals(State.GOOD, NutrientTargets.limitState(0.0, 5.0))
        assertEquals(State.GOOD, NutrientTargets.limitState(5.0, 5.0)) // exactly 100%
    }

    @Test
    fun `up to 120 percent of a limit is a warning`() {
        assertEquals(State.WARN, NutrientTargets.limitState(5.5, 5.0))
        assertEquals(State.WARN, NutrientTargets.limitState(6.0, 5.0)) // exactly 120%
    }

    @Test
    fun `above 120 percent of a limit is over`() {
        assertEquals(State.OVER, NutrientTargets.limitState(6.1, 5.0))
    }

    @Test
    fun `a zero limit gives no verdict`() {
        assertEquals(State.NEUTRAL, NutrientTargets.limitState(3.0, 0.0))
    }
}
