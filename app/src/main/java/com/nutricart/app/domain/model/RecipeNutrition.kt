package com.nutricart.app.domain.model

/**
 * A recipe as the meal-plan generator sees it: nutrition PER ONE SERVING
 * (computed by summing its ingredients — recipes never store nutrition).
 */
data class RecipeNutrition(
    val id: Long,
    val name: String,
    val slots: Set<MealSlot>,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val isVegetarian: Boolean,
    val containsPork: Boolean,
    val allergens: Set<Allergen>,
    val cookTimeMin: Int,
)
