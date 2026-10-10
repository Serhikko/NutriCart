package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nutricart.app.ui.dashboard.DashboardContent
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.TodayRange
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.stats.StatsContent
import com.nutricart.app.ui.stats.StatsRange
import com.nutricart.app.ui.stats.StatsUiState
import org.junit.Test

/** The Today tab: the dashboard body and the Week / Month / 90 days statistics. */
class DashboardScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Dashboard(
        state: DashboardUiState,
        range: TodayRange = TodayRange.TODAY,
        stats: (StatsRange) -> StatsUiState = FakeData::stats,
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

    @Test
    @KeyScreen
    fun today() = shoot("dashboard-today") { Dashboard(FakeData.dashboard()) }

    @Test
    @Tall(2300)
    fun todayFull() = shoot("dashboard-today-full") { Dashboard(FakeData.dashboard()) }

    @Test
    fun newUser() = shoot("dashboard-today-new-user") { Dashboard(FakeData.dashboardNewUser()) }

    @Test
    @Tall(2000)
    fun overTarget() = shoot("dashboard-today-over-target") { Dashboard(FakeData.dashboardOver()) }

    @Test
    fun loading() = shoot("dashboard-today-loading") { Dashboard(DashboardUiState()) }

    @Test
    @KeyScreen
    fun week() = shoot("dashboard-stats-week") { Dashboard(FakeData.dashboard(), TodayRange.WEEK) }

    @Test
    @Tall(1900)
    fun monthFull() = shoot("dashboard-stats-month-full") { Dashboard(FakeData.dashboard(), TodayRange.MONTH) }

    @Test
    fun ninety() = shoot("dashboard-stats-90") { Dashboard(FakeData.dashboard(), TodayRange.NINETY) }

    @Test
    fun statsEmpty() = shoot("dashboard-stats-empty") {
        Dashboard(FakeData.dashboard(), TodayRange.WEEK, stats = { FakeData.statsEmpty() })
    }
}
