package com.nutricart.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nutricart.app.R
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.common.CookedPortionsDialog
import com.nutricart.app.ui.dashboard.DashboardContent
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.mealplan.MealPlanContent
import com.nutricart.app.ui.navigation.AppBottomBar
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.quickadd.QuickAddSheetContent
import org.junit.Test

/**
 * The "+" sheet over the Today tab, the two sheets it opens, and the cooked-portions sheet the plan
 * and the recipe share. Every sheet is shot as the app shell shows it: the tab receded onto black
 * behind it (light theme) and the tab bar gone.
 */
class QuickAddScreenshots(variant: Variant) : ScreenshotTest(variant) {

    /** A tab on the receding stage with the floating tab bar over it, and [sheet] above both. */
    @Composable
    private fun Shell(route: String, page: @Composable () -> Unit, sheet: @Composable () -> Unit) {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            Box(Modifier.fillMaxSize()) {
                SheetStage { page() }
                AppBottomBar(
                    route, {}, {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = !presentation.presenting,
                )
            }
            sheet()
        }
    }

    @Composable
    private fun Today() {
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

    @Composable
    private fun Plan() {
        MealPlanContent(
            state = FakeData.mealPlan(),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenRecipe = { _, _ -> },
            onGenerate = {},
            onToggleLock = {},
            onSwap = {},
            onAddToDiary = {},
            onCook = { _, _ -> },
        )
    }

    @Composable
    private fun QuickAdd(waterMl: Int? = 750, onAddWater: (Int) -> Unit = {}, onUndoWater: () -> Unit = {}) {
        QuickAddSheetContent(
            today = FakeData.today,
            waterMl = waterMl,
            weightKg = 78.4,
            onDismiss = {},
            onLogFood = {},
            onScanFood = {},
            onAddWater = onAddWater,
            onUndoWater = onUndoWater,
            onAddWorkout = { _, _ -> },
            onLogWeight = {},
        )
    }

    @Composable
    private fun SheetOverToday(waterMl: Int? = 750) {
        Shell(Routes.DASHBOARD, page = { Today() }) { QuickAdd(waterMl) }
    }

    // ------------------------------------------------------------------ quick add

    @Test
    @KeyScreen
    fun sheet() = shoot("quick-add-sheet") { SheetOverToday() }

    /** Before today's water total has arrived: a dash, empty glasses, Undo off. */
    @Test
    fun sheetNoWaterYet() = shoot("quick-add-sheet-water-loading") { SheetOverToday(waterMl = null) }

    /** A full day of water: every glass full, the total past 2 litres. */
    @Test
    fun sheetWaterFull() = shoot("quick-add-sheet-water-full") { SheetOverToday(waterMl = 2250) }

    /** "Add workout" in the sheet opens the workout sheet over it. */
    @Test
    @KeyScreen
    fun workoutDialog() = shoot(
        "quick-add-workout-dialog",
        interact = { onAllNodesWithText(str(R.string.workout_add)).onLast().performScrollTo().performClick() },
    ) { SheetOverToday() }

    /** "Weight" opens the weight sheet, prefilled with the latest weight. */
    @Test
    @KeyScreen
    fun weightDialog() = shoot(
        "quick-add-weight-dialog",
        // At large text sizes the row is below the fold: the sheet scrolls to it first, as a finger would.
        interact = { onAllNodesWithText(str(R.string.weight_card_title)).onLast().performScrollTo().performClick() },
    ) { SheetOverToday() }

    // ------------------------------------------------------------------ the workout sheet alone

    /** As Today's workouts card opens it: straight over the tab. */
    @Test
    fun workoutSheet() = shoot("workout-sheet") {
        Shell(Routes.DASHBOARD, page = { Today() }) { AddWorkoutDialog(weightKg = 78.4, onConfirm = { _, _ -> }, onDismiss = {}) }
    }

    /** A repetitions type: the amount turns into reps (20) and the estimate follows. */
    @Test
    fun workoutSheetReps() = shoot(
        "workout-sheet-reps",
        interact = { onAllNodesWithText(str(R.string.workout_push_ups)).onLast().performScrollTo().performClick() },
    ) {
        Shell(Routes.DASHBOARD, page = { Today() }) { AddWorkoutDialog(weightKg = 78.4, onConfirm = { _, _ -> }, onDismiss = {}) }
    }

    // ------------------------------------------------------------------ cooked portions

    @Test
    @KeyScreen
    fun cookedSheet() = shoot("cooked-sheet") {
        Shell(Routes.PLAN, page = { Plan() }) { CookedPortionsDialog(onConfirm = {}, onDismiss = {}) }
    }

    /** Two taps on "More": three portions (the count hands off, "Less" is on again). */
    @Test
    fun cookedSheetThree() = shoot(
        "cooked-sheet-three",
        interact = {
            repeat(2) { onAllNodesWithContentDescription(str(R.string.amount_more)).onLast().performScrollTo().performClick() }
        },
    ) {
        Shell(Routes.PLAN, page = { Plan() }) { CookedPortionsDialog(onConfirm = {}, onDismiss = {}) }
    }

    // ------------------------------------------------------------------ motion

    /** The sheet rises on the sheet spring while Today recedes onto black and the tab bar leaves. */
    @Test
    fun presentFrames() = shootFrames(
        "quick-add-present",
        times = listOf(32, 100, 200, 320, 480, 800),
        firstOpen = false,
    ) { SheetOverToday() }

    /**
     * "+250 ml" a second after the sheet opened: the fifth glass fills on the bouncy spring while the
     * total hands off 1,000 → 1,250 digit by digit (only the changed digits move).
     */
    @Test
    fun waterTapFrames() = shootFrames(
        "quick-add-water-tap",
        times = listOf(960, 1060, 1160, 1300, 1500, 1900),
        firstOpen = false,
    ) {
        var ml by remember { mutableStateOf(1000) }
        // The tap, once the sheet has settled (the test clock drives the wait).
        LaunchedEffect(Unit) {
            motionDelay(1000)
            ml += 250
        }
        Shell(Routes.DASHBOARD, page = { Today() }) { QuickAdd(waterMl = ml) }
    }
}
