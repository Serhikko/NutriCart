package com.nutricart.app.screenshots

import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.domain.model.RecipeNutrition
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeItemUi
import com.nutricart.app.ui.fridge.FridgeUiState
import com.nutricart.app.ui.mealplan.MealPlanUiState
import com.nutricart.app.ui.mealplan.PlanDayUi
import com.nutricart.app.ui.mealplan.PlanMealUi
import com.nutricart.app.ui.mealplan.RecipeDetailUiState
import com.nutricart.app.ui.shopping.ShoppingItemUi
import com.nutricart.app.ui.shopping.ShoppingUiState
import kotlin.math.roundToInt

/**
 * Extra states for the plan, recipe, fridge and shopping shots (G3), built on [FakeData]: a
 * regenerated week, a recipe id the book does not have, the assistant's other states, and the
 * shopping list before anything is ticked.
 */
object PlanFakes {

    /**
     * The same week dealt again: every unlocked meal gets the next dish of its slot, the locked one
     * stays. Days keep their places, so the screen hands each changed dish over.
     */
    fun mealPlanRegenerated(): MealPlanUiState {
        val before = FakeData.mealPlan()
        val bySlot = listOf(
            FakeData.oatmeal, FakeData.syrnyky, FakeData.omelette, FakeData.borscht, FakeData.buckwheat, FakeData.plov,
            FakeData.salmon, FakeData.turkey, FakeData.tofu, FakeData.yogurt, FakeData.apple, FakeData.hummus,
        ).groupBy { it.slots.first() }
        var id = 100L
        val days = before.days.map { day ->
            val meals = day.meals.map { meal ->
                if (meal.isLocked) {
                    meal
                } else {
                    val choices = bySlot.getValue(meal.slot)
                    val next = choices[(choices.indexOfFirst { it.id == meal.recipeId } + 1) % choices.size]
                    // A portion that keeps the meal near its old size, the way the generator sizes one.
                    val factor = (meal.kcal / next.kcal).coerceIn(.8, 1.5).let { (it * 20).roundToInt() / 20.0 }
                    meal.copy(id = ++id, recipeId = next.id, recipeName = next.name, kcal = (next.kcal * factor).roundToInt(), portionFactor = factor)
                }
            }
            PlanDayUi(day.epochDay, meals, meals.sumOf { it.kcal })
        }
        return before.copy(days = days)
    }

    /** One meal swapped for another dish of its slot (the second meal of today). */
    fun mealPlanSwapped(): MealPlanUiState {
        val before = FakeData.mealPlan()
        val today = before.days.first()
        val swapped = today.meals.mapIndexed { i, m ->
            if (i == 0) m.copy(id = 900, recipeId = FakeData.omelette.id, recipeName = FakeData.omelette.name, kcal = kcal(FakeData.omelette, 1.1), portionFactor = 1.1) else m
        }
        return before.copy(days = listOf(PlanDayUi(today.epochDay, swapped, swapped.sumOf { it.kcal })) + before.days.drop(1))
    }

    private fun kcal(r: RecipeNutrition, factor: Double) = (r.kcal * factor).roundToInt()

    /** A plan whose days land within ±5% of the target: every day card carries "On target". */
    fun mealPlanOnTarget(): MealPlanUiState = FakeData.mealPlan().copy(targetKcal = 1900)

    /** The recipe screen after the ViewModel answered: no such recipe. */
    fun recipeMissing() = RecipeDetailUiState(loading = false, details = null)

    /** Still loading. */
    fun recipeLoading() = RecipeDetailUiState()

    fun aiReady() = AiUiState(hasKey = true)

    fun aiError() = AiUiState(hasKey = true, result = AiResult.Offline)

    fun aiTruncated(): AiUiState {
        val ok = FakeData.aiAnswer().result as AiResult.Ok
        return AiUiState(hasKey = true, result = ok.copy(truncated = true))
    }

    /** The fridge right after "Add what you have": a new item in its aisle. */
    fun fridgeWithAdded(): FridgeUiState {
        val before = FakeData.fridge()
        val added = FridgeItemUi("Сир твердий", Aisle.DAIRY_EGGS, 200.0, 200)
        // In its aisle, after the rows that were there: the others keep their places.
        val byAisle = before.itemsByAisle.mapValues { (aisle, list) -> if (aisle == Aisle.DAIRY_EGGS) list + added else list }
        return before.copy(itemsByAisle = byAisle, itemCount = before.itemCount + 1)
    }

    /** The shopping list before anything is ticked (no "Bought N" yet). */
    fun shoppingNothingTicked(): ShoppingUiState {
        val state = FakeData.shopping()
        return state.mapItems { it.copy(isChecked = false, movedToFridge = false) }
    }

    /** [this] with one item ticked or unticked, the way the ViewModel would recount it. */
    fun ShoppingUiState.tick(item: ShoppingItemUi, checked: Boolean): ShoppingUiState =
        mapItems { if (it.id == item.id) it.copy(isChecked = checked) else it }

    private fun ShoppingUiState.mapItems(f: (ShoppingItemUi) -> ShoppingItemUi): ShoppingUiState {
        val items = itemsByAisle.mapValues { (_, list) -> list.map(f) }
        val all = items.values.flatten()
        return copy(
            itemsByAisle = items,
            movableCount = all.count { it.isChecked && !it.alreadyHave && !it.movedToFridge },
        )
    }

    /** Ticks are kept by name in the frames tests; a lookup by name across the aisles. */
    fun ShoppingUiState.item(name: String): ShoppingItemUi = itemsByAisle.values.flatten().first { it.name == name }

    /** Meals of the plan by id (for the cooked sheet test). */
    fun MealPlanUiState.meal(id: Long): PlanMealUi = days.flatMap { it.meals }.first { it.id == id }
}
