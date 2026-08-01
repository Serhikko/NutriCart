package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.Aisle

/**
 * Master list of ingredients used by recipes (seeded from assets/recipes.json).
 * The shopping list will merge amounts by ingredient and group them by aisle.
 * Ids come from the seed file — no autoGenerate, so recipe references stay stable.
 */
@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val aisle: Aisle,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
    /** e.g. one egg ≈ 55 g — lets the UI say "~2 pcs" next to grams. */
    val gramsPerPiece: Double?,
)
