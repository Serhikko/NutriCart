package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.FridgeDao
import com.nutricart.app.data.local.dao.PlanDao
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.seed.RecipeSeeder
import com.nutricart.app.data.local.entity.FridgeItemEntity
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.domain.model.Aisle
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the user has at home. Groceries come in two ways — picked by hand, or
 * carried over from the shopping list once they are bought — and leave one
 * way: cooking a recipe.
 *
 * Every number here is grams, raw and unrounded, like everywhere else.
 */
@Singleton
class FridgeRepository @Inject constructor(
    private val fridgeDao: FridgeDao,
    private val recipeDao: RecipeDao,
    private val planDao: PlanDao,
    private val planRepository: PlanRepository,
    private val seeder: RecipeSeeder,
) {

    fun observeAll(): Flow<List<FridgeItemEntity>> = fridgeDao.observeAll()

    /** A one-shot read, for building the assistant's prompt. */
    suspend fun items(): List<FridgeItemEntity> = fridgeDao.allItems()

    /**
     * The 65 seeded ingredients — the only names the fridge accepts, because a
     * hand-typed name would never equal a recipe's ingredient and cooking would
     * then silently deduct nothing. Seeds on first use, like every other screen
     * that touches recipes.
     */
    suspend fun pickableIngredients(): List<IngredientEntity> {
        seeder.ensureSeeded()
        return recipeDao.allIngredients()
    }

    /** Buying more of something adds to it. */
    suspend fun add(name: String, aisle: Aisle, grams: Double) {
        require(grams > 0.0) { "grams must be positive" }
        fridgeDao.addGrams(name, aisle, grams)
    }

    /** The correction path: an absolute amount, where 0 g means "gone". */
    suspend fun setGrams(name: String, aisle: Aisle, grams: Double) =
        fridgeDao.setGrams(name, aisle, grams)

    suspend fun remove(name: String) = fridgeDao.delete(name)

    /**
     * "Bought it": the ticked rows of the shopping list become stock, and the
     * same rows are marked so a second tap cannot add them again. Both writes
     * are ONE transaction — a kill in between would re-arm exactly the
     * double-add the flag exists to prevent.
     *
     * The RAW totalGrams is moved, never the rounded display value and never
     * the piece count (which is deliberately rounded UP): either would
     * overstate what was really bought, and the drift would compound weekly.
     */
    suspend fun putBought(rows: List<ShoppingListItemEntity>) {
        val movable = rows.filter { it.isChecked && !it.alreadyHave && !it.movedToFridge }
        if (movable.isEmpty()) return
        fridgeDao.putBought(
            additions = movable.map {
                FridgeItemEntity(it.ingredientName, it.aisle, it.totalGrams)
            },
            shoppingIds = movable.map { it.id },
        )
    }

    /**
     * Cooking a recipe: subtract what it used. Ingredients the fridge never
     * had match no row, so cooking with unregistered food quietly changes
     * nothing — the app is a tracker, not a gatekeeper.
     */
    suspend fun cook(recipeId: Long, portionFactor: Double, portions: Int = 1) {
        val needs = recipeDao.ingredientsFor(recipeId)
            .map { FridgeMath.Use(it.name, it.grams) }
        fridgeDao.takeOut(FridgeMath.uses(needs, portionFactor, portions))
    }

    /**
     * "What can I cook right now?" — ranked over the recipes this profile may
     * eat, so the diet and allergen filter can never be bypassed by a
     * "you have the ingredients" shortcut.
     */
    suspend fun cookableNow(profile: UserProfileEntity): List<FridgeMath.Match> {
        val recipes = planRepository.recipesForProfile(profile)
        if (recipes.isEmpty()) return emptyList()
        val nameById = recipes.associate { it.id to it.name }
        val needsById = recipeDao.ingredientAmountsForRecipes(recipes.map { it.id })
            .groupBy { it.recipeId }
        val stock = fridgeDao.allItems().associate { it.ingredientName to it.grams }
        // What the week already plans is pushed to the bottom: the fridge is
        // filled from the shopping list, which is built FROM the plan, so
        // otherwise this card would just read the plan back.
        val today = LocalDate.now().toEpochDay()
        val planned = planDao.onDays((today..today + 6).toList()).map { it.recipeId }.toSet()
        return FridgeMath.rank(
            plannedIds = planned,
            recipes = recipes.map { recipe ->
                FridgeMath.RecipeNeed(
                    recipeId = recipe.id,
                    name = nameById.getValue(recipe.id),
                    needs = needsById[recipe.id].orEmpty()
                        .map { FridgeMath.Use(it.name, it.grams) },
                )
            },
            stock = stock,
        )
    }
}
