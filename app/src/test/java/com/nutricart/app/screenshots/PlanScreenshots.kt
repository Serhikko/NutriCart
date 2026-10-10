package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nutricart.app.R
import com.nutricart.app.ui.mealplan.MealPlanContent
import com.nutricart.app.ui.mealplan.MealPlanUiState
import com.nutricart.app.ui.mealplan.RecipeDetailContent
import com.nutricart.app.ui.mealplan.RecipeDetailUiState
import com.nutricart.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import org.junit.Test

/** The Plan tab and the recipe screen it opens. */
class PlanScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Plan(state: MealPlanUiState) = TabFrame(Routes.PLAN) {
        MealPlanContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpenRecipe = { _, _ -> },
            onGenerate = {},
            onToggleLock = {},
            onSwap = {},
            onAddToDiary = {},
            onCook = { _, _ -> },
        )
    }

    /**
     * The plan driven like the ViewModel drives it: a regenerate shows the spinner, the new week
     * lands while it still runs, and the spinner goes 120 ms later (when the locks glint).
     */
    @Composable
    private fun LivePlan(holder: MutableState<MealPlanUiState>) {
        LaunchedEffect(holder.value.generating) {
            if (holder.value.generating) {
                delay(120)
                holder.value = holder.value.copy(generating = false)
            }
        }
        Plan(holder.value)
    }

    @Composable
    private fun Recipe(state: RecipeDetailUiState) =
        RecipeDetailContent(state = state, onBack = {}, onCook = {}, backLabel = stringResource(R.string.plan_title))

    @Test
    @KeyScreen
    fun plan() = shoot("meal-plan") { Plan(FakeData.mealPlan()) }

    @Test
    @Tall(2600)
    fun planFull() = shoot("meal-plan-full") { Plan(FakeData.mealPlan()) }

    @Test
    fun generating() = shoot("meal-plan-generating") { Plan(FakeData.mealPlan().copy(generating = true)) }

    @Test
    @KeyScreen
    fun onTarget() = shoot("meal-plan-on-target") { Plan(PlanFakes.mealPlanOnTarget()) }

    @Test
    @KeyScreen
    fun empty() = shoot("meal-plan-empty") { Plan(FakeData.mealPlanEmpty()) }

    @Test
    fun emptyGenerating() = shoot("meal-plan-empty-generating") { Plan(FakeData.mealPlanEmpty().copy(generating = true)) }

    @Test
    fun loading() = shoot("meal-plan-loading") { Plan(MealPlanUiState()) }

    @Test
    fun cookedDialog() = shoot(
        "meal-plan-cooked-dialog",
        interact = {
            onAllNodesWithContentDescription(str(R.string.fridge_cooked_action))[0].performClick()
        },
    ) { Plan(FakeData.mealPlan()) }

    /** First open of the day: the title rises, the day cards rise in turn, their rings sweep. */
    @Test
    fun openFrames() = shootFrames("meal-plan-open", times = listOf(120, 300, 600, 1200)) { Plan(PlanFakes.mealPlanOnTarget()) }

    /**
     * Regenerate deals a new week: each changed dish hands its name over down the week, kcal digits
     * hand off, the day rings re-sweep, the locked lunch stays and its lock glints.
     */
    @Test
    fun regenerateFrames() {
        val holder = mutableStateOf(FakeData.mealPlan())
        shootFrames(
            "meal-plan-regenerate",
            times = listOf(60, 120, 180, 240, 320, 520, 900),
            firstOpen = false,
            interact = { runOnUiThread { holder.value = PlanFakes.mealPlanRegenerated().copy(generating = true) } },
        ) { LivePlan(holder) }
    }

    /** Swap does the same for one row. */
    @Test
    fun swapFrames() {
        val holder = mutableStateOf(FakeData.mealPlan())
        shootFrames(
            "meal-plan-swap",
            times = listOf(60, 120, 180, 240, 600),
            firstOpen = false,
            interact = { runOnUiThread { holder.value = PlanFakes.mealPlanSwapped() } },
        ) { Plan(holder.value) }
    }

    @Test
    @KeyScreen
    fun recipe() = shoot("recipe-detail") { Recipe(FakeData.recipeDetail()) }

    @Test
    fun recipeScaledCooked() = shoot("recipe-detail-scaled-cooked") {
        Recipe(FakeData.recipeDetail(factor = 1.5, cooked = true))
    }

    @Test
    @Tall(1900)
    fun recipeFull() = shoot("recipe-detail-full") { Recipe(FakeData.recipeDetail(factor = 1.2)) }

    @Test
    fun recipeLoading() = shoot("recipe-detail-loading") { Recipe(PlanFakes.recipeLoading()) }

    /** An id the recipe book does not have: a calm message and the way back, not an endless spinner. */
    @Test
    @KeyScreen
    fun recipeMissing() = shoot("recipe-detail-missing") { Recipe(PlanFakes.recipeMissing()) }

    @Test
    fun recipeCookedDialog() = shoot(
        "recipe-detail-cooked-dialog",
        interact = { onNodeWithText(str(R.string.fridge_cooked_action)).performClick() },
    ) { Recipe(FakeData.recipeDetail(factor = 1.2)) }

    /** First open: the kcal digits arrive one by one over the glow, the cards rise. */
    @Test
    fun recipeOpenFrames() = shootFrames("recipe-detail-open", times = listOf(120, 300, 600, 1200)) {
        Recipe(FakeData.recipeDetail(factor = 1.2))
    }

    /** Cooking: the docked button turns into the green "Taken out of the fridge" while its check draws. */
    @Test
    fun recipeCookFrames() {
        val holder = mutableStateOf(FakeData.recipeDetail(factor = 1.2))
        shootFrames(
            "recipe-detail-cook",
            times = listOf(60, 200, 400, 800),
            firstOpen = false,
            interact = { runOnUiThread { holder.value = holder.value.copy(cooked = true) } },
        ) { Recipe(holder.value) }
    }
}
