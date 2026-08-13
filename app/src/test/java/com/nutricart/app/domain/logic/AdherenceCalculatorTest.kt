package com.nutricart.app.domain.logic

import com.nutricart.app.domain.logic.AdherenceCalculator.DayState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorTest {

    @Test
    fun `no entries means an empty day`() {
        assertEquals(DayState.EMPTY, AdherenceCalculator.dayState(null, 2000.0))
        assertEquals(DayState.EMPTY, AdherenceCalculator.dayState(0.0, 2000.0))
    }

    @Test
    fun `a broken target gives no verdict`() {
        assertEquals(DayState.EMPTY, AdherenceCalculator.dayState(1500.0, 0.0))
    }

    @Test
    fun `under the target is good`() {
        assertEquals(DayState.GOOD, AdherenceCalculator.dayState(1800.0, 2000.0))
    }

    @Test
    fun `the 5 percent tolerance is still good`() {
        assertEquals(DayState.GOOD, AdherenceCalculator.dayState(2100.0, 2000.0)) // exactly +5%
    }

    @Test
    fun `above the tolerance is over`() {
        assertEquals(DayState.OVER, AdherenceCalculator.dayState(2101.0, 2000.0))
    }
}
