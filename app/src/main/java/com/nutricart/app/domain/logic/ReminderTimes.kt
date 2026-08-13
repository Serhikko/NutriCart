package com.nutricart.app.domain.logic

import java.time.Duration
import java.time.LocalDateTime

/**
 * When should a daily reminder fire next? Pure math, so the tricky midnight
 * cases are JUnit-tested; the WorkManager plumbing just asks for a delay.
 */
object ReminderTimes {

    /** The next occurrence of [minutesOfDay] strictly AFTER [now]. */
    fun nextTrigger(now: LocalDateTime, minutesOfDay: Int): LocalDateTime {
        val todayAt = now.toLocalDate().atStartOfDay().plusMinutes(minutesOfDay.toLong())
        // "Exactly now" schedules for tomorrow: the worker that is firing at
        // this minute must not chain a second run for the same minute.
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    fun delayMillis(now: LocalDateTime, minutesOfDay: Int): Long =
        Duration.between(now, nextTrigger(now, minutesOfDay)).toMillis()
}
