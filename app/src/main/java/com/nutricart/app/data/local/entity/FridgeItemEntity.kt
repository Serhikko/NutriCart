package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.Aisle

/**
 * What the user actually has at home. One row per ingredient, keyed by NAME —
 * the same identity the shopping list already uses, so groceries can move from
 * the list into the fridge and cooking can take them out again without a single
 * name-to-id lookup that could fail.
 *
 * Only the seeded ingredients can end up here (the add dialog is a picker, not
 * a text field): a hand-typed name would never equal a recipe's ingredient
 * name, so cooking would silently deduct nothing.
 *
 * grams is stored RAW, exactly like shopping_list_item.totalGrams — rounding
 * happens at display time only.
 */
@Entity(tableName = "fridge_item")
data class FridgeItemEntity(
    @PrimaryKey val ingredientName: String,
    /** A copy of the ingredient's aisle, so the list groups without a join. */
    val aisle: Aisle,
    val grams: Double,
)
