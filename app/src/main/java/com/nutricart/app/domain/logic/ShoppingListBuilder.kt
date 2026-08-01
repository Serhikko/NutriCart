package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.Aisle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure math of the shopping list: merge duplicate ingredients across all
 * selected days, sum the grams, group by supermarket aisle.
 */
object ShoppingListBuilder {

    /** One ingredient usage, already scaled by its meal's portion factor. */
    data class IngredientAmount(
        val name: String,
        val aisle: Aisle,
        val grams: Double,
        val gramsPerPiece: Double?,
    )

    /** One merged line of the final list. */
    data class Item(
        val name: String,
        val aisle: Aisle,
        val totalGrams: Double,
        /** ceil, never round down: better one egg too many than one too few. */
        val pieces: Int?,
    )

    fun build(amounts: List<IngredientAmount>): List<Item> =
        amounts
            .groupBy { it.name }
            .map { (name, rows) ->
                val total = rows.sumOf { it.grams }
                val perPiece = rows.firstNotNullOfOrNull { it.gramsPerPiece }
                Item(
                    name = name,
                    aisle = rows.first().aisle,
                    totalGrams = total,
                    pieces = perPiece?.let { ceil(total / it).toInt() },
                )
            }
            // Aisle declaration order = a sensible walk through the store.
            .sortedWith(compareBy({ it.aisle.ordinal }, { it.name }))

    /** Display rounding: big amounts to 5 g, small ones to whole grams (min 1). */
    fun displayGrams(totalGrams: Double): Int =
        if (totalGrams < 10.0) totalGrams.roundToInt().coerceAtLeast(1)
        else (totalGrams / 5.0).roundToInt() * 5
}
