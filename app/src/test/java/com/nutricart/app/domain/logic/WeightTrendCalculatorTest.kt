package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTrendCalculatorTest {

    private val delta = 0.0001

    @Test
    fun `perfectly linear weight loss gives the exact slope`() {
        // Losing 0.1 kg per day starting at 80 kg.
        val points = (0L..9L).map { day -> (20_000L + day) to (80.0 - 0.1 * day) }

        val trend = WeightTrendCalculator.calculate(points)!!

        assertEquals(-0.1, trend.slopeKgPerDay, delta)
        assertEquals(-0.7, trend.slopeKgPerWeek, delta)
        // The line passes through the actual measurements.
        assertEquals(80.0, trend.weightAt(20_000L), delta)
        assertEquals(79.1, trend.weightAt(20_009L), delta)
    }

    @Test
    fun `constant weight gives a flat trend`() {
        val points = (0L..6L).map { day -> (20_000L + day) to 75.5 }

        val trend = WeightTrendCalculator.calculate(points)!!

        assertEquals(0.0, trend.slopeKgPerDay, delta)
        assertEquals(75.5, trend.weightAt(20_003L), delta)
    }

    @Test
    fun `two points define the line through both`() {
        val trend = WeightTrendCalculator.calculate(
            listOf(20_000L to 90.0, 20_010L to 89.0)
        )!!

        assertEquals(-0.1, trend.slopeKgPerDay, delta)
        assertEquals(89.5, trend.weightAt(20_005L), delta)
    }

    @Test
    fun `a single point has no trend`() {
        assertNull(WeightTrendCalculator.calculate(listOf(20_000L to 80.0)))
    }

    @Test
    fun `points all on the same day have no trend`() {
        assertNull(
            WeightTrendCalculator.calculate(
                listOf(20_000L to 80.0, 20_000L to 80.4)
            )
        )
    }
}
