package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.PlanDao
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.dao.ShoppingDao
import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.ShoppingListBuilder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and stores the shopping list for the plan week. */
@Singleton
class ShoppingRepository @Inject constructor(
    private val planDao: PlanDao,
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val settings: SettingsDataStore,
) {

    fun observeList(): Flow<List<ShoppingListItemEntity>> = shoppingDao.observeAll()

    fun observeSelectedDays(): Flow<Set<Long>> = settings.shoppingSelectedDays

    /**
     * (Re)builds the ONE current list from the selected plan days.
     * Regeneration is a MERGE: totals refresh, but isChecked/alreadyHave are
     * carried over by ingredient name, so mid-shopping progress survives
     * plan tweaks AND calendar days. Removed ingredients simply disappear.
     */
    suspend fun regenerate(selectedDays: Set<Long>) {
        val meals = planDao.onDays(selectedDays.toList())
        val amountsByRecipe = recipeDao
            .ingredientAmountsForRecipes(meals.map { it.recipeId }.distinct())
            .groupBy { it.recipeId }

        // Every ingredient of every planned meal, scaled by its portion factor.
        val amounts = meals.flatMap { meal ->
            amountsByRecipe[meal.recipeId].orEmpty().map { row ->
                ShoppingListBuilder.IngredientAmount(
                    name = row.name,
                    aisle = row.aisle,
                    grams = row.grams * meal.portionFactor,
                    gramsPerPiece = row.gramsPerPiece,
                )
            }
        }
        val items = ShoppingListBuilder.build(amounts)

        val previous = shoppingDao.allItems().associateBy { it.ingredientName }
        val rows = items.map { item ->
            ShoppingListItemEntity(
                ingredientName = item.name,
                aisle = item.aisle,
                totalGrams = item.totalGrams,
                pieces = item.pieces,
                isChecked = previous[item.name]?.isChecked ?: false,
                alreadyHave = previous[item.name]?.alreadyHave ?: false,
                // Carried with the tick, not reset: a row that is still ticked
                // from the last trip and already sits in the fridge must not
                // become movable again, or those groceries land there twice.
                movedToFridge = previous[item.name]?.movedToFridge ?: false,
            )
        }
        shoppingDao.replaceAll(rows)
        settings.setShoppingSelectedDays(selectedDays)
    }

    suspend fun setChecked(id: Long, checked: Boolean) = shoppingDao.setChecked(id, checked)

    suspend fun setAlreadyHave(id: Long, alreadyHave: Boolean) =
        shoppingDao.setAlreadyHave(id, alreadyHave)
}
