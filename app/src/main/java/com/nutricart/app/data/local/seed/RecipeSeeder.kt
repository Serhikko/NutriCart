package com.nutricart.app.data.local.seed

import android.content.Context
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.local.entity.RecipeEntity
import com.nutricart.app.data.local.entity.RecipeIngredientEntity
import com.nutricart.app.data.local.entity.RecipeStepEntity
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.MealSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// The shape of assets/recipes.json (two top-level arrays).
@Serializable
private data class SeedFileDto(
    val ingredients: List<SeedIngredientDto>,
    val recipes: List<SeedRecipeDto>,
)

@Serializable
private data class SeedIngredientDto(
    val id: Long,
    val name: String,
    val aisle: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
    val gramsPerPiece: Double? = null,
)

@Serializable
private data class SeedRecipeDto(
    val id: Long,
    val name: String,
    val slots: List<String>,
    val cookTimeMin: Int,
    val isVegetarian: Boolean,
    val containsPork: Boolean,
    val allergens: List<String> = emptyList(),
    val steps: List<String>,
    val ingredients: List<SeedRecipeIngredientDto>,
)

@Serializable
private data class SeedRecipeIngredientDto(
    val ingredientId: Long,
    val grams: Double,
)

/**
 * Fills the recipe tables from assets/recipes.json on first use.
 * Ingredients are inserted FIRST (recipe_ingredient has a RESTRICT foreign key
 * to them), and the whole seed runs in one DAO transaction.
 * The Mutex makes a parallel first call wait instead of double-seeding.
 */
@Singleton
class RecipeSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recipeDao: RecipeDao,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun ensureSeeded() {
        mutex.withLock {
            if (recipeDao.countRecipes() > 0) return

            val seed = withContext(Dispatchers.IO) {
                val text = context.assets.open(SEED_FILE)
                    .bufferedReader()
                    .use { it.readText() }
                json.decodeFromString<SeedFileDto>(text)
            }

            val ingredients = seed.ingredients.map { dto ->
                IngredientEntity(
                    id = dto.id,
                    name = dto.name,
                    aisle = Aisle.valueOf(dto.aisle), // wrong value = loud crash in dev
                    kcalPer100g = dto.kcalPer100g,
                    proteinPer100g = dto.proteinPer100g,
                    fatPer100g = dto.fatPer100g,
                    carbsPer100g = dto.carbsPer100g,
                    gramsPerPiece = dto.gramsPerPiece,
                )
            }
            val recipes = seed.recipes.map { dto ->
                RecipeEntity(
                    id = dto.id,
                    name = dto.name,
                    cookTimeMin = dto.cookTimeMin,
                    isVegetarian = dto.isVegetarian,
                    containsPork = dto.containsPork,
                    allergens = dto.allergens.map { Allergen.valueOf(it) },
                    suitableSlots = dto.slots.map { MealSlot.valueOf(it) },
                )
            }
            val steps = seed.recipes.flatMap { dto ->
                dto.steps.mapIndexed { index, text ->
                    RecipeStepEntity(recipeId = dto.id, stepNumber = index + 1, text = text)
                }
            }
            val recipeIngredients = seed.recipes.flatMap { dto ->
                dto.ingredients.map {
                    RecipeIngredientEntity(
                        recipeId = dto.id,
                        ingredientId = it.ingredientId,
                        grams = it.grams,
                    )
                }
            }

            recipeDao.seedAll(ingredients, recipes, steps, recipeIngredients)
        }
    }

    companion object {
        private const val SEED_FILE = "recipes.json"
    }
}
