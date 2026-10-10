package com.nutricart.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.DashboardContent
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.quickadd.QuickAddSheetContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import org.junit.Test

/** The "+" sheet over the Today tab, and the two dialogs it opens. */
class QuickAddScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun SheetOverToday(waterMl: Int? = 750) {
        TabFrame(Routes.DASHBOARD) {
            DashboardContent(
                state = FakeData.dashboard(),
                snackbarHostState = remember { SnackbarHostState() },
                onOpenSettings = {},
                onOpenRecipe = { _, _ -> },
                onRefresh = {},
                onGrantPermissions = {},
                onAddWater = {},
                onUndoWater = {},
                onAddWorkout = { _, _ -> },
                onDeleteWorkout = {},
                statsBody = {},
            )
        }
        QuickAddSheetContent(
            today = FakeData.today,
            waterMl = waterMl,
            weightKg = 78.4,
            onDismiss = {},
            onLogFood = {},
            onScanFood = {},
            onAddWater = {},
            onUndoWater = {},
            onAddWorkout = { _, _ -> },
            onLogWeight = {},
        )
    }

    @Test
    @KeyScreen
    fun sheet() = shoot("quick-add-sheet") { SheetOverToday() }

    @Test
    fun sheetNoWaterYet() = shoot("quick-add-sheet-water-loading") { SheetOverToday(waterMl = null) }

    @Test
    fun workoutDialog() = shoot(
        "quick-add-workout-dialog",
        interact = { onAllNodesWithText(str(R.string.workout_add)).onLast().performClick() },
    ) { SheetOverToday() }

    @Test
    fun weightDialog() = shoot(
        "quick-add-weight-dialog",
        interact = { onAllNodesWithText(str(R.string.weight_card_title)).onLast().performClick() },
    ) { SheetOverToday() }
}
