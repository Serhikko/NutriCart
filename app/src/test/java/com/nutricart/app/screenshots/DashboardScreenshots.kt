package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.dashboard.DashboardContent
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.TodayRange
import com.nutricart.app.ui.navigation.AddedHandOff
import com.nutricart.app.ui.navigation.LocalAddedHandOff
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.stats.StatsContent
import com.nutricart.app.ui.stats.StatsRange
import com.nutricart.app.ui.stats.StatsUiState
import org.junit.Test

/**
 * The Today tab: the day body (ring, menu, macros, activity, workouts, water, weight) and the Week /
 * Month / 90 days statistics, at rest and in motion: the first open of the day, landing in the zone,
 * over the target, back from Add, a range switch.
 */
class DashboardScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Dashboard(
        state: DashboardUiState,
        range: TodayRange = TodayRange.TODAY,
        stats: (StatsRange) -> StatsUiState = DashboardFakes::stats,
    ) = TabFrame(Routes.DASHBOARD) {
        DashboardContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpenSettings = {},
            onOpenRecipe = { _, _ -> },
            onRefresh = {},
            onGrantPermissions = {},
            onAddWater = {},
            onUndoWater = {},
            onAddWorkout = { _, _ -> },
            onDeleteWorkout = {},
            statsBody = { r -> StatsContent(stats(r), onPreviousMonth = {}, onNextMonth = {}) },
            initialRange = range,
        )
    }

    // ------------------------------------------------------------------ Today at rest

    @Test
    @KeyScreen
    fun today() = shoot("dashboard-today") { Dashboard(FakeData.dashboard()) }

    @Test
    @KeyScreen
    @Tall(2600)
    fun todayFull() = shoot("dashboard-today-full") { Dashboard(FakeData.dashboard()) }

    @Test
    fun newUser() = shoot("dashboard-today-new-user") { Dashboard(FakeData.dashboardNewUser()) }

    @Test
    @Tall(2000)
    fun overTarget() = shoot("dashboard-today-over-target") { Dashboard(FakeData.dashboardOver()) }

    @Test
    fun zone() = shoot("dashboard-today-zone") { Dashboard(DashboardFakes.zone()) }

    @Test
    fun noDataHint() = shoot("dashboard-today-no-data") { Dashboard(DashboardFakes.noData()) }

    @Test
    fun loading() = shoot("dashboard-today-loading") { Dashboard(DashboardUiState()) }

    /** Scrolled past the title: the compact bar says "Today · 855 left". */
    @Test
    fun scrolled() = shoot(
        "dashboard-today-scrolled",
        interact = { onNode(hasScrollToIndexAction()).performScrollToIndex(3) },
    ) { Dashboard(FakeData.dashboard()) }

    // ------------------------------------------------------------------ Today in motion

    /**
     * The first open of the day: title rise, ring sweep, digits, halo, light pass, cards cascade. A key
     * screen, so "Remove animations" (rm) shows every frame complete and at rest.
     */
    @Test
    @KeyScreen
    fun firstOpenFrames() = shootFrames("dashboard-today-first", times = listOf(60, 250, 600, 1200, 2000)) {
        Dashboard(FakeData.dashboard())
    }

    /** A revisit: one calm fade-up, numbers already in place. */
    @Test
    fun revisitFrames() = shootFrames("dashboard-today-revisit", times = listOf(60, 200, 400), firstOpen = false) {
        Dashboard(FakeData.dashboard())
    }

    /** Landing in the zone: the badge pops at 1,150 ms, the glow pulses twice and one light pass runs. */
    @Test
    fun zoneFrames() = shootFrames("dashboard-today-zone-first", times = listOf(900, 1300, 1500, 1800, 3400)) {
        Dashboard(DashboardFakes.zone())
    }

    /** Over the target: the ring runs past 12 o'clock into its ink second lap; the centre says "kcal over". */
    @Test
    fun overFrames() = shootFrames("dashboard-today-over-first", times = listOf(600, 1000, 1600)) {
        Dashboard(FakeData.dashboardOver())
    }

    /** Back from Add: the ring re-sweeps from 1,300 to 1,385, the digits hand off (940 → 855), "Added to Lunch". */
    @Test
    fun backFromAddFrames() {
        DashboardFakes.seedBeforeAdd()
        shootFrames("dashboard-today-back-from-add", times = listOf(60, 300, 500, 800, 1400), firstOpen = false) {
            CompositionLocalProvider(LocalAddedHandOff provides AddedHandOff(MealSlot.LUNCH) {}) {
                Dashboard(FakeData.dashboard())
            }
        }
    }

    // ------------------------------------------------------------------ Statistics

    @Test
    @KeyScreen
    fun week() = shoot("dashboard-stats-week") { Dashboard(FakeData.dashboard(), TodayRange.WEEK) }

    @Test
    @Tall(2100)
    fun weekFull() = shoot("dashboard-stats-week-full") { Dashboard(FakeData.dashboard(), TodayRange.WEEK) }

    @Test
    fun month() = shoot("dashboard-stats-month") { Dashboard(FakeData.dashboard(), TodayRange.MONTH) }

    @Test
    @KeyScreen
    @Tall(2100)
    fun monthFull() = shoot("dashboard-stats-month-full") { Dashboard(FakeData.dashboard(), TodayRange.MONTH) }

    @Test
    @Tall(1500)
    fun ninety() = shoot("dashboard-stats-90") { Dashboard(FakeData.dashboard(), TodayRange.NINETY) }

    @Test
    @Tall(1500)
    fun statsEmpty() = shoot("dashboard-stats-empty") {
        Dashboard(FakeData.dashboard(), TodayRange.WEEK, stats = { FakeData.statsEmpty() })
    }

    /** Only today logged: the caption counts finished days, so it names the target alone. */
    @Test
    fun weekTodayOnly() = shoot("dashboard-stats-week-today-only") {
        Dashboard(FakeData.dashboard(), TodayRange.WEEK, stats = { DashboardFakes.statsTodayOnly() })
    }

    @Test
    fun statsLoading() = shoot("dashboard-stats-loading") {
        Dashboard(FakeData.dashboard(), TodayRange.WEEK, stats = { StatsUiState() })
    }

    /** The week's first open: digits arrive, markers pop, bars rise out of the axis, the goal wipes in. */
    @Test
    @KeyScreen
    fun weekFirstOpenFrames() = shootFrames("dashboard-stats-week-first", times = listOf(60, 400, 800, 1300, 2000)) {
        Dashboard(FakeData.dashboard(), TodayRange.WEEK)
    }

    /** Week → Month: the average hands its digits off (2,238 → 2,105) and the markers fold away. */
    @Test
    fun weekToMonthFrames() = shootFrames(
        "dashboard-stats-week-to-month",
        times = listOf(60, 200, 400, 900),
        firstOpen = false,
        interact = { onNodeWithText(str(R.string.stats_range_month)).performClick() },
    ) { Dashboard(FakeData.dashboard(), TodayRange.WEEK) }
}
