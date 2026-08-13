package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item of a saved meal — a frozen copy of the diary entry it was created
 * from. Nutrition is a SNAPSHOT (same rule as the diary): logging the saved
 * meal later reproduces exactly what was eaten when it was saved, even if the
 * cached product changed or disappeared (productId then goes null via
 * SET_NULL, like in the diary).
 */
@Entity(
    tableName = "saved_meal_item",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE, // deleting the meal removes its items
        ),
        ForeignKey(
            entity = FoodProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("mealId"), Index("productId")],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val productId: String?,
    val name: String,
    val grams: Double?,
    val servings: Double?,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    // Detail-nutrient snapshots (v0.11), same null-means-unknown rule as the diary.
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saltG: Double? = null,
    val saturatedFatG: Double? = null,
)
