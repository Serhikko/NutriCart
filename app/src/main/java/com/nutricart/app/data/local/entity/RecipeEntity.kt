package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.MealSlot

/**
 * A recipe (seeded from assets/recipes.json). Nutrition is NOT stored:
 * it is computed by summing the linked ingredients (single source of truth).
 * All recipes are defined PER ONE SERVING; the meal plan scales them with a
 * portion factor.
 */
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val cookTimeMin: Int,
    val isVegetarian: Boolean,
    val containsPork: Boolean,
    /** Stored as CSV of enum names via Converters. */
    val allergens: List<Allergen>,
    /** Which diary slots this dish fits (a soup is lunch/dinner, not breakfast). */
    val suitableSlots: List<MealSlot>,
)
