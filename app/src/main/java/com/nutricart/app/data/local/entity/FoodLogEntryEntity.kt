package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.MealSlot

/**
 * One eaten item in the diary.
 *
 * name + kcal/macros are SNAPSHOTS taken at logging time: history must never
 * change, even if the cached product is refreshed or evicted later. That is
 * also why productId may become null (SET_NULL) without breaking anything.
 *
 * grams == null together with productId == null marks a snapshot entry
 * without a product — today that's a meal-plan recipe copied to the diary,
 * later also quick-add typed calories.
 * servings != null means the user logged in portions; grams stays the
 * source of truth for all math either way.
 */
@Entity(
    tableName = "food_log_entry",
    foreignKeys = [
        ForeignKey(
            entity = FoodProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("epochDay"), Index("productId")],
)
data class FoodLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: MealSlot,
    val productId: String?,
    val name: String,
    val grams: Double?,
    val servings: Double?,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    // Detail-nutrient snapshots (v0.11): null = unknown at log time, never 0 —
    // day sums count only what is actually known.
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saltG: Double? = null,
    val saturatedFatG: Double? = null,
    val loggedAtEpochMillis: Long,
)
