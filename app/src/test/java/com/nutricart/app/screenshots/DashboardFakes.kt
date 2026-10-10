package com.nutricart.app.screenshots

import com.nutricart.app.domain.logic.AdherenceCalculator
import com.nutricart.app.domain.logic.AdherenceCalculator.DayState
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.HcBannerState
import com.nutricart.app.ui.ember.seedLastShown
import com.nutricart.app.ui.stats.DayBar
import com.nutricart.app.ui.stats.StatsRange
import com.nutricart.app.ui.stats.StatsUiState
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Today and Statistics states the Ember mocks show (the harness world: target 2,240 kcal, 1,385
 * eaten), on top of [FakeData]: the zone, the day before a meal was added, and statistics whose
 * week, month and 90 days differ, so a range switch has digits to hand off.
 */
object DashboardFakes {
    private const val TARGET = 2240.0

    private val today: Long get() = LocalDate.now().toEpochDay()

    /** In the zone: 2,090 of 2,240 eaten (93%), 150 left. */
    fun zone(): DashboardUiState = FakeData.dashboard().copy(
        eatenKcal = 2090,
        remainingKcal = 150,
        eatenProteinG = 128,
        eatenFatG = 68,
        eatenCarbsG = 231,
        eatenFiberG = 27.5,
        eatenSugarsG = 47.0,
        eatenSaltG = 4.6,
        eatenSatFatG = 19.5,
        waterMl = 2000,
    )

    /** Health Connect is connected but has nothing yet: the "no data" hint over an ordinary day. */
    fun noData(): DashboardUiState = FakeData.dashboard().copy(
        showNoDataHint = true,
        steps = null,
        activeKcal = null,
        exerciseMinutes = null,
        sleepMinutes = null,
        avgHeartRateBpm = null,
        workouts = FakeData.workouts.filter { !it.isFromWatch },
        hcBanner = HcBannerState.NONE,
    )

    /**
     * Back from Add: Today last showed 1,300 eaten (940 left) before Food search logged 85 kcal to
     * lunch; now it shows [FakeData.dashboard]'s 1,385 (855 left), so every digit hands off in place.
     * Call before composing.
     */
    fun seedBeforeAdd() {
        seedLastShown("ring/$today", 1300f)
        seedLastShown("remaining/$today", 940L)
        seedLastShown("eaten/$today", 1300L)
        seedLastShown("water/$today", 1250L)
    }

    /** The mock's week, Sunday to today: on plan, within 5%, on plan, over, on plan, within, today so far. */
    private val week = listOf(2150.0, 2310.0, 2080.0, 2440.0, 2190.0, 2260.0, 1385.0)

    /** The mock's 30 days (three of them not logged), ending today. */
    private val month = listOf(
        2180.0, 2310.0, 0.0, 2100.0, 2250.0, 2190.0, 2440.0, 2080.0, 2210.0, 2160.0, 2290.0, 2230.0, 0.0, 2170.0, 2050.0,
        2240.0, 2610.0, 2190.0, 2120.0, 2230.0, 2260.0, 2180.0, 2150.0, 2310.0, 2080.0, 2440.0, 2190.0, 0.0, 2420.0, 1385.0,
    )

    private fun bars(values: List<Double>): List<DayBar> = values.mapIndexed { i, kcal ->
        val day = today - (values.size - 1 - i)
        DayBar(day, kcal, AdherenceCalculator.dayState(kcal.takeIf { it > 0.0 }, TARGET))
    }

    /** This month as the mock draws it: on plan, yesterday over, the day before not logged. */
    private fun calendar(): Map<Long, DayState> {
        val now = LocalDate.now()
        val first = YearMonth.from(now).atDay(1)
        return (0 until now.dayOfMonth).associate { k ->
            val day = first.plusDays(k.toLong()).toEpochDay()
            day to when (today - day) {
                0L -> DayState.GOOD
                1L -> DayState.OVER
                2L -> DayState.EMPTY
                else -> DayState.GOOD
            }
        }.filterValues { it != DayState.EMPTY }
    }

    /**
     * A first week: only today is logged so far. No day is finished, so the hero's caption names the
     * target instead of "0 of 0 days on plan".
     */
    fun statsTodayOnly(): StatsUiState {
        val bars = bars(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1385.0))
        return FakeData.stats(StatsRange.WEEK).copy(
            bars = bars,
            avgEatenKcal = 1385,
            loggedDays = 1,
            goodDays = 1,
            calendarStates = mapOf(today to DayState.GOOD),
        )
    }

    /** Statistics for [range]: the mock's week and month; 90 days continue the month's rhythm. */
    fun stats(range: StatsRange): StatsUiState {
        val values = when (range) {
            StatsRange.WEEK -> week
            StatsRange.MONTH -> month
            StatsRange.NINETY -> List(60) { i -> if (i % 17 == 9) 0.0 else 2060.0 + ((i * 53) % 11) * 38.0 } + month
        }
        val bars = bars(values)
        val logged = bars.count { it.state != DayState.EMPTY }
        val average = when (range) {
            StatsRange.WEEK -> 2238
            StatsRange.MONTH -> 2105
            StatsRange.NINETY -> 2141
        }
        return FakeData.stats(range).copy(
            bars = bars,
            avgEatenKcal = average,
            loggedDays = logged,
            goodDays = bars.count { it.state == DayState.GOOD },
            calendarStates = calendar(),
        )
    }
}
