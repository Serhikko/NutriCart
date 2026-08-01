package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.local.entity.RecipeEntity
import com.nutricart.app.data.local.entity.RecipeIngredientEntity
import com.nutricart.app.data.local.entity.RecipeStepEntity
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.RecipeNutrition

/** A recipe with its per-serving nutrition summed from ingredients. */
data class RecipeNutritionRow(
    val id: Long,
    val name: String,
    val cookTimeMin: Int,
    val isVegetarian: Boolean,
    val containsPork: Boolean,
    val allergens: List<Allergen>,
    val suitableSlots: List<MealSlot>,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
) {
    fun toDomain() = RecipeNutrition(
        id = id, name = name, slots = suitableSlots.toSet(),
        kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG,
        isVegetarian = isVegetarian, containsPork = containsPork,
        allergens = allergens.toSet(), cookTimeMin = cookTimeMin,
    )
}

/** One ingredient line of a recipe, ready for display. */
data class IngredientAmountRow(
    val name: String,
    val grams: Double,
    val gramsPerPiece: Double?,
)

/** Ingredient usage across recipes — raw material for the shopping list. */
data class RecipeIngredientAmountRow(
    val recipeId: Long,
    val name: String,
    val aisle: Aisle,
    val gramsPerPiece: Double?,
    val grams: Double,
)

@Dao
interface RecipeDao {

    @Query("SELECT COUNT(*) FROM recipe")
    suspend fun countRecipes(): Int

    @Insert suspend fun insertIngredients(items: List<IngredientEntity>)
    @Insert suspend fun insertRecipes(items: List<RecipeEntity>)
    @Insert suspend fun insertSteps(items: List<RecipeStepEntity>)
    @Insert suspend fun insertRecipeIngredients(items: List<RecipeIngredientEntity>)

    /** One transaction: either the whole seed lands, or none of it. */
    @Transaction
    suspend fun seedAll(
        ingredients: List<IngredientEntity>,
        recipes: List<RecipeEntity>,
        steps: List<RecipeStepEntity>,
        recipeIngredients: List<RecipeIngredientEntity>,
    ) {
        insertIngredients(ingredients)
        insertRecipes(recipes)
        insertSteps(steps)
        insertRecipeIngredients(recipeIngredients)
    }

    /**
     * THE nutrition query: per-serving kcal and macros of every recipe,
     * computed by summing (ingredient per-100g values * grams / 100).
     * This is the single place recipe nutrition comes from.
     */
    @Query(
        """
        SELECT r.id AS id, r.name AS name, r.cookTimeMin AS cookTimeMin,
               r.isVegetarian AS isVegetarian, r.containsPork AS containsPork,
               r.allergens AS allergens, r.suitableSlots AS suitableSlots,
               SUM(i.kcalPer100g * ri.grams / 100.0) AS kcal,
               SUM(i.proteinPer100g * ri.grams / 100.0) AS proteinG,
               SUM(i.fatPer100g * ri.grams / 100.0) AS fatG,
               SUM(i.carbsPer100g * ri.grams / 100.0) AS carbsG
        FROM recipe r
        JOIN recipe_ingredient ri ON ri.recipeId = r.id
        JOIN ingredient i ON i.id = ri.ingredientId
        GROUP BY r.id
        """
    )
    suspend fun recipesWithNutrition(): List<RecipeNutritionRow>

    @Query("SELECT * FROM recipe_step WHERE recipeId = :recipeId ORDER BY stepNumber")
    suspend fun stepsFor(recipeId: Long): List<RecipeStepEntity>

    @Query(
        """
        SELECT i.name AS name, ri.grams AS grams, i.gramsPerPiece AS gramsPerPiece
        FROM recipe_ingredient ri
        JOIN ingredient i ON i.id = ri.ingredientId
        WHERE ri.recipeId = :recipeId
        ORDER BY ri.grams DESC
        """
    )
    suspend fun ingredientsFor(recipeId: Long): List<IngredientAmountRow>

    @Query(
        """
        SELECT ri.recipeId AS recipeId, i.name AS name, i.aisle AS aisle,
               i.gramsPerPiece AS gramsPerPiece, ri.grams AS grams
        FROM recipe_ingredient ri
        JOIN ingredient i ON i.id = ri.ingredientId
        WHERE ri.recipeId IN (:recipeIds)
        """
    )
    suspend fun ingredientAmountsForRecipes(recipeIds: List<Long>): List<RecipeIngredientAmountRow>
}
