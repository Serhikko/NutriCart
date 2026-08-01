package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val today = 20_000L

    @Test
    fun `no logged days means no streak`() {
        assertEquals(0, StreakCalculator.calculate(emptySet(), today))
    }

    @Test
    fun `today plus two previous days is a streak of three`() {
        val logged = setOf(today, today - 1, today - 2)
        assertEquals(3, StreakCalculator.calculate(logged, today))
    }

    @Test
    fun `an empty today does not break yesterday's streak`() {
        val logged = setOf(today - 1, today - 2, today - 3)
        assertEquals(3, StreakCalculator.calculate(logged, today))
    }

    @Test
    fun `a gap resets the streak`() {
        // Logged today and the day BEFORE yesterday — the hole stops the count.
        val logged = setOf(today, today - 2, today - 3)
        assertEquals(1, StreakCalculator.calculate(logged, today))
    }

    @Test
    fun `old history without today or yesterday gives zero`() {
        val logged = setOf(today - 5, today - 6)
        assertEquals(0, StreakCalculator.calculate(logged, today))
    }
}
