package com.nutricart.app.domain.logic

import com.nutricart.app.domain.logic.ShoppingListBuilder.IngredientAmount
import com.nutricart.app.domain.model.Aisle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingListBuilderTest {

    @Test
    fun `duplicate ingredients across days merge into one line with summed grams`() {
        val items = ShoppingListBuilder.build(
            listOf(
                IngredientAmount("Гречка", Aisle.GRAINS_PASTA, 80.0, null),   // Monday
                IngredientAmount("Гречка", Aisle.GRAINS_PASTA, 120.0, null),  // Thursday
                IngredientAmount("Куряче філе", Aisle.MEAT_FISH, 150.0, null),
            )
        )

        assertEquals(2, items.size)
        val buckwheat = items.first { it.name == "Гречка" }
        assertEquals(200.0, buckwheat.totalGrams, 0.001)
    }

    @Test
    fun `items come out grouped in aisle order then alphabetical`() {
        val items = ShoppingListBuilder.build(
            listOf(
                IngredientAmount("Вівсянка", Aisle.GRAINS_PASTA, 60.0, null),
                IngredientAmount("Яблуко", Aisle.PRODUCE, 180.0, null),
                IngredientAmount("Банан", Aisle.PRODUCE, 120.0, null),
                IngredientAmount("Лосось", Aisle.MEAT_FISH, 200.0, null),
            )
        )

        // PRODUCE (declared first) comes before MEAT_FISH and GRAINS_PASTA,
        // and inside PRODUCE "Банан" sorts before "Яблуко".
        assertEquals(listOf("Банан", "Яблуко", "Лосось", "Вівсянка"), items.map { it.name })
    }

    @Test
    fun `pieces are rounded UP so the user never buys too few`() {
        val items = ShoppingListBuilder.build(
            listOf(
                IngredientAmount("Яйце", Aisle.DAIRY_EGGS, 110.0, gramsPerPiece = 55.0), // exactly 2
                IngredientAmount("Яйце", Aisle.DAIRY_EGGS, 30.0, gramsPerPiece = 55.0),  // 140 g -> 2.5 pcs
            )
        )

        assertEquals(3, items.single().pieces) // ceil(140 / 55) = 3
    }

    @Test
    fun `ingredients without piece size have null pieces`() {
        val items = ShoppingListBuilder.build(
            listOf(IngredientAmount("Рис", Aisle.GRAINS_PASTA, 180.0, null))
        )
        assertNull(items.single().pieces)
    }

    @Test
    fun `display grams round to 5 for big amounts and whole grams for small`() {
        assertEquals(200, ShoppingListBuilder.displayGrams(198.3))
        assertEquals(85, ShoppingListBuilder.displayGrams(87.3))
        assertEquals(4, ShoppingListBuilder.displayGrams(4.2))   // not 5, not 0
        assertEquals(1, ShoppingListBuilder.displayGrams(0.4))   // never 0
    }
}
