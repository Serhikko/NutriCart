package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimesTest {

    private val morning = LocalDateTime.of(2026, 8, 13, 7, 30)

    @Test
    fun `a time still ahead today fires today`() {
        val next = ReminderTimes.nextTrigger(morning, 8 * 60) // 08:00
        assertEquals(LocalDateTime.of(2026, 8, 13, 8, 0), next)
    }

    @Test
    fun `a time already passed fires tomorrow`() {
        val next = ReminderTimes.nextTrigger(morning, 7 * 60) // 07:00 < 07:30
        assertEquals(LocalDateTime.of(2026, 8, 14, 7, 0), next)
    }

    @Test
    fun `exactly now fires tomorrow, not immediately again`() {
        val next = ReminderTimes.nextTrigger(morning, 7 * 60 + 30)
        assertEquals(LocalDateTime.of(2026, 8, 14, 7, 30), next)
    }

    @Test
    fun `midnight reminder from late evening fires the next midnight`() {
        val lateEvening = LocalDateTime.of(2026, 8, 13, 23, 59)
        val next = ReminderTimes.nextTrigger(lateEvening, 0) // 00:00
        assertEquals(LocalDateTime.of(2026, 8, 14, 0, 0), next)
    }

    @Test
    fun `the delay is always positive`() {
        assertTrue(ReminderTimes.delayMillis(morning, 8 * 60) > 0)
        assertTrue(ReminderTimes.delayMillis(morning, 7 * 60) > 0)
        assertTrue(ReminderTimes.delayMillis(morning, 7 * 60 + 30) > 0)
    }
}
