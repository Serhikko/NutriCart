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
 * grams == null together with productId == null means a quick-add entry
 * (typed calories without a product; UI for that comes later).
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
    val loggedAtEpochMillis: Long,
)
