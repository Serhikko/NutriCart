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
import com.nutricart.app.domain.model.MealSlot
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

    /** One day's meals — the dashboard's "today's menu" card. */
    fun observeDay(epochDay: Long): Flow<List<PlannedMealEntity>> =
        planDao.observeRange(epochDay, epochDay)

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
     * Generates 7 days starting at [startEpochDay], in COOKING GROUPS:
     * one menu is generated per group and copied to each of its days
     * (batch cooking — see MealPlanGenerator.buildCookingGroups).
     * Everything is generated in memory FIRST and the database is written
     * only when every group succeeded — a failure never changes the existing
     * plan (including its locks).
     * Returns null on success, or the first failure reason.
     */
    suspend fun generateWeek(
        startEpochDay: Long,
        targets: DailyTargets,
        snacksPerDay: Int,
        cookingSessionsPerWeek: Int,
        recipes: List<RecipeNutrition>,
        generator: MealPlanGenerator,
    ): PlanFailureReason? {
        val allById = nutritionById()
        val allowedIds = recipes.map { it.id }.toSet()
        val templateKeys = generator.slotTemplate(snacksPerDay)
            .map { it.slot to it.position }
            .toSet()
        val days = (0..6).map { startEpochDay + it }
        val groups = MealPlanGenerator.buildCookingGroups(days, cookingSessionsPerWeek)
        val newMeals = mutableListOf<PlannedMealEntity>()
        val staleLockedIds = mutableListOf<Long>()

        for (group in groups) {
            // Locks apply per GROUP: the first still-valid lock of a slot wins
            // and its dish is eaten on every day of the group. Locks that lost
            // the race, fail the CURRENT diet filter (an allergen added in
            // Settings must never survive a lock) or no longer fit the day
            // template are collected and unlocked only AFTER the whole week
            // succeeded — a FAILED generation leaves the plan untouched.
            val winningLocks = mutableMapOf<Pair<MealSlot, Int>, Pair<Long, PlannedMealDraft>>()
            for (day in group) {
                for (row in planDao.lockedOnDay(day)) {
                    val key = row.slot to row.position
                    val nutrition = allById[row.recipeId]
                    if (nutrition == null || row.recipeId !in allowedIds ||
                        key !in templateKeys || key in winningLocks
                    ) {
                        staleLockedIds += row.id
                    } else {
                        winningLocks[key] = day to PlannedMealDraft(
                            row.slot, row.position, nutrition, row.portionFactor, isLocked = true,
                        )
                    }
                }
            }
            val locked = winningLocks.values.map { it.second }

            when (val result = generator.generateDay(targets, recipes, snacksPerDay, locked)) {
                is PlanDayResult.Failure -> return result.reason
                is PlanDayResult.Success -> {
                    // The group's menu is written to EVERY day of the group.
                    for (day in group) {
                        for (draft in result.meals) {
                            val lockOriginDay = winningLocks[draft.slot to draft.position]?.first
                            // The winning locked row already exists on its own day.
                            if (draft.isLocked && day == lockOriginDay) continue
                            newMeals += PlannedMealEntity(
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
        }

        // Every group succeeded — now stale locks may go, then the week is swapped.
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
