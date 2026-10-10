package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.nutricart.app.R
import com.nutricart.app.ui.mealplan.MealPlanContent
import com.nutricart.app.ui.mealplan.MealPlanUiState
import com.nutricart.app.ui.mealplan.RecipeDetailContent
import com.nutricart.app.ui.navigation.Routes
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

    @Test
    @KeyScreen
    fun plan() = shoot("meal-plan") { Plan(FakeData.mealPlan()) }

    @Test
    @Tall(2600)
    fun planFull() = shoot("meal-plan-full") { Plan(FakeData.mealPlan()) }

    @Test
    fun generating() = shoot("meal-plan-generating") { Plan(FakeData.mealPlan().copy(generating = true)) }

    @Test
    fun empty() = shoot("meal-plan-empty") { Plan(FakeData.mealPlanEmpty()) }

    @Test
    fun loading() = shoot("meal-plan-loading") { Plan(MealPlanUiState()) }

    @Test
    fun cookedDialog() = shoot(
        "meal-plan-cooked-dialog",
        interact = {
            onAllNodesWithContentDescription(str(R.string.fridge_cooked_action))[0].performClick()
        },
    ) { Plan(FakeData.mealPlan()) }

    @Test
    @KeyScreen
    fun recipe() = shoot("recipe-detail") {
        RecipeDetailContent(state = FakeData.recipeDetail(), onBack = {}, onCook = {})
    }

    @Test
    fun recipeScaledCooked() = shoot("recipe-detail-scaled-cooked") {
        RecipeDetailContent(state = FakeData.recipeDetail(factor = 1.5, cooked = true), onBack = {}, onCook = {})
    }
}
