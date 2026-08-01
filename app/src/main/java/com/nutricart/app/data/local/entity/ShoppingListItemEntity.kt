package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.Aisle

/**
 * One line of the shopping list, materialized so checkbox state survives
 * app restarts. The table holds exactly ONE current list (rebuilding replaces
 * it wholesale) — a list built on Monday must still be there, checked items
 * and all, when the user shops on Tuesday.
 * ingredientName is a COPY (not a foreign key): the list lives its own life
 * in the store, whatever happens to recipes meanwhile. Regeneration MERGES
 * by name: amounts update, isChecked/alreadyHave are preserved.
 */
@Entity(
    tableName = "shopping_list_item",
    indices = [Index(value = ["ingredientName"], unique = true)],
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientName: String,
    val aisle: Aisle,
    val totalGrams: Double,
    /** ceil(total / gramsPerPiece) for countable items ("~6 pcs"), else null. */
    val pieces: Int?,
    val isChecked: Boolean,
    val alreadyHave: Boolean,
)
