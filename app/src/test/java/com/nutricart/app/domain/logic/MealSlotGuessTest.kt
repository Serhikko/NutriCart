package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealSlotGuessTest {

    private fun at(hour: Int, minute: Int = 0) = MealSlotGuess.forTime(LocalTime.of(hour, minute))

    @Test
    fun `morning is breakfast`() {
        assertEquals(MealSlot.BREAKFAST, at(8))
    }

    @Test
    fun `midday is lunch`() {
        assertEquals(MealSlot.LUNCH, at(13))
    }

    @Test
    fun `evening is dinner`() {
        assertEquals(MealSlot.DINNER, at(19))
    }

    @Test
    fun `the small hours are a snack, not tomorrow's breakfast`() {
        assertEquals(MealSlot.SNACK, at(0))
        assertEquals(MealSlot.SNACK, at(3, 30))
    }

    @Test
    fun `every boundary falls on the later meal`() {
        assertEquals(MealSlot.SNACK, at(4, 59))
        assertEquals(MealSlot.BREAKFAST, at(5))
        assertEquals(MealSlot.BREAKFAST, at(10, 59))
        assertEquals(MealSlot.LUNCH, at(11))
        assertEquals(MealSlot.LUNCH, at(15, 59))
        assertEquals(MealSlot.DINNER, at(16))
        assertEquals(MealSlot.DINNER, at(21, 59))
        assertEquals(MealSlot.SNACK, at(22))
    }

    @Test
    fun `a full day reaches all four slots`() {
        assertEquals(MealSlot.entries.toSet(), (0..23).map { at(it) }.toSet())
    }
}
