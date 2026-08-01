package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.IngredientAmountRow
import com.nutricart.app.data.local.dao.PlanDao
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.entity.PlannedMealEntity
import com.nutricart.app.data.local.seed.RecipeSeeder
import com.nutricart.app.domain.logic.MealPlanGenerator
import com.nutricart.app.domain.logic.PlanDayResult
import com.nutricart.app.domain.logic.PlanFailureReason
import com.nutricart.app.domain.logic.PlannedMealDraft
import com.nutricart.app.domain.logic.filterRecipesForDiet
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.RecipeNutrition
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Steps + ingredient amounts of one recipe, for the detail screen. */
data class RecipeDetails(
    val nutrition: RecipeNutrition,
    val steps: List<String>,
    val ingredients: List<IngredientAmountRow>,
)

/** The weekly meal plan: generation, swapping, locking, reading. */
@Singleton
class PlanRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val planDao: PlanDao,
    private val seeder: RecipeSeeder,
) {

    fun observeWeek(startEpochDay: Long): Flow<List<PlannedMealEntity>> =
        planDao.observeRange(startEpochDay, startEpochDay + 6)

    /** ALL recipes by id (unfiltered) — for displaying any planned meal. */
    suspend fun nutritionById(): Map<Long, RecipeNutrition> {
        seeder.ensureSeeded()
        return recipeDao.recipesWithNutrition().associate { it.id to it.toDomain() }
    }

    /** Recipes this user may eat, per their diet settings. */
    suspend fun recipesForProfile(profile: UserProfileEntity): List<RecipeNutrition> {
        seeder.ensureSeeded()
        return filterRecipesForDiet(
            recipes = recipeDao.recipesWithNutrition().map { it.toDomain() },
            isVegetarian = profile.isVegetarian,
            noPork = profile.noPork,
            allergies = profile.allergies.toSet(),
        )
    }

    /**
     * Generates 7 days starting at [startEpochDay]. All days are generated in
     * memory FIRST and the database is written only when every day succeeded —
     * a failure never changes the existing plan (including its locks).
     * Returns null on success, or the first day's failure reason.
     */
    suspend fun generateWeek(
        startEpochDay: Long,
        targets: DailyTargets,
        snacksPerDay: Int,
        recipes: List<RecipeNutrition>,
        generator: MealPlanGenerator,
    ): PlanFailureReason? {
        val allById = nutritionById()
        val allowedIds = recipes.map { it.id }.toSet()
        val templateKeys = generator.slotTemplate(snacksPerDay)
            .map { it.slot to it.position }
            .toSet()
        val newMeals = mutableListOf<PlannedMealEntity>()
        val staleLockedIds = mutableListOf<Long>()

        for (offset in 0..6) {
            val day = startEpochDay + offset
            // Locked meals stay as fixed input — but ONLY if they still pass
            // the CURRENT diet filter (an allergen added in Settings must never
            // survive a lock) and still fit the day template (snacksPerDay may
            // have changed). Rows failing either check are collected and, once
            // the whole week generated successfully, unlocked so replaceWeek
            // removes them like any free slot. (Not unlocked earlier: a FAILED
            // generation must leave the plan and its locks untouched.)
            val lockedRows = planDao.lockedOnDay(day)
            val locked = mutableListOf<PlannedMealDraft>()
            for (row in lockedRows) {
                val stillValid = row.recipeId in allowedIds &&
                    (row.slot to row.position) in templateKeys
                if (stillValid) {
                    allById[row.recipeId]?.let { nutrition ->
                        locked += PlannedMealDraft(
                            row.slot, row.position, nutrition, row.portionFactor, isLocked = true,
                        )
                    }
                } else {
                    staleLockedIds += row.id
                }
            }
            when (val result = generator.generateDay(targets, recipes, snacksPerDay, locked)) {
                is PlanDayResult.Failure -> return result.reason
                is PlanDayResult.Success -> {
                    newMeals += result.meals
                        .filterNot { it.isLocked } // locked rows already exist in the DB
                        .map { draft ->
                            PlannedMealEntity(
                                epochDay = day,
                                slot = draft.slot,
                                position = draft.position,
                                recipeId = draft.recipe.id,
                                portionFactor = draft.portionFactor,
                                isLocked = false,
                            )
                        }
                }
            }
        }

        // Every day succeeded — now stale locks may go, then the week is swapped.
        staleLockedIds.forEach { planDao.setLocked(it, false) }
        planDao.replaceWeek(startEpochDay, startEpochDay + 6, newMeals)
        return null
    }

    /**
     * Swaps one meal for a different random recipe of the same slot, keeping
     * the meal's kcal roughly unchanged so the day stays within its band.
     */
    suspend fun swapMeal(
        mealId: Long,
        recipes: List<RecipeNutrition>,
        random: Random,
    ): Boolean {
        val meal = planDao.byId(mealId) ?: return false
        if (meal.isLocked) return false
        val allById = nutritionById()
        val current = allById[meal.recipeId] ?: return false

        val candidates = recipes.filter { meal.slot in it.slots && it.id != meal.recipeId }
        if (candidates.isEmpty()) return false

        val replacement = candidates.random(random)
        val currentKcal = current.kcal * meal.portionFactor
        val factor = MealPlanGenerator.roundFactor(
            (currentKcal / replacement.kcal)
                .coerceIn(MealPlanGenerator.MIN_FACTOR, MealPlanGenerator.MAX_FACTOR)
        )

        planDao.replaceRecipe(mealId, replacement.id, factor)
        return true
    }

    suspend fun setLocked(mealId: Long, locked: Boolean) = planDao.setLocked(mealId, locked)

    suspend fun recipeDetails(recipeId: Long): RecipeDetails? {
        val nutrition = nutritionById()[recipeId] ?: return null
        return RecipeDetails(
            nutrition = nutrition,
            steps = recipeDao.stepsFor(recipeId).map { it.text },
            ingredients = recipeDao.ingredientsFor(recipeId),
        )
    }
}
