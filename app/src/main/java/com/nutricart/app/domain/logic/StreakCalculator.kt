package com.nutricart.app.domain.logic

/**
 * The diary streak: how many days IN A ROW the user has logged at least one
 * food entry. A small motivation nudge on the dashboard.
 */
object StreakCalculator {

    /**
     * [loggedDays] = days that have at least one diary entry.
     * Counting starts from today, or from yesterday if today is still empty —
     * an empty morning must not show the streak as broken.
     */
    fun calculate(loggedDays: Set<Long>, today: Long): Int {
        var day = if (today in loggedDays) today else today - 1
        var streak = 0
        while (day in loggedDays) {
            streak++
            day--
        }
        return streak
    }
}
