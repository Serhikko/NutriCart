package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.MealSlot

/**
 * One generated meal of the weekly plan.
 * The unique index = one meal per (day, slot, position); snacks use positions 0/1.
 * isLocked freezes BOTH the recipe AND the portion factor: regeneration
 * treats locked meals as fixed input and only refills the free slots.
 */
@Entity(
    tableName = "planned_meal",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.RESTRICT, // a planned recipe cannot disappear
        )
    ],
    indices = [
        Index(value = ["epochDay", "slot", "position"], unique = true),
        Index("recipeId"),
    ],
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slot: MealSlot,
    val position: Int,
    val recipeId: Long,
    /** Ingredient grams are multiplied by this (0.5..2.0, in 0.05 steps). */
    val portionFactor: Double,
    val isLocked: Boolean,
)
