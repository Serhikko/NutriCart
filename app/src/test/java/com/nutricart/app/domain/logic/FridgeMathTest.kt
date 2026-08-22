package com.nutricart.app.domain.logic

import com.nutricart.app.domain.logic.FridgeMath.Use
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridgeMathTest {

    private val chicken = Use("Chicken", 200.0)
    private val rice = Use("Rice", 120.0)

    @Test
    fun `one portion takes exactly what the recipe says`() {
        val uses = FridgeMath.uses(listOf(chicken, rice), portionFactor = 1.0)
        assertEquals(200.0, uses[0].grams, 0.0001)
        assertEquals(120.0, uses[1].grams, 0.0001)
    }

    @Test
    fun `the portion factor scales every ingredient`() {
        val uses = FridgeMath.uses(listOf(chicken), portionFactor = 1.5)
        assertEquals(300.0, uses.single().grams, 0.0001)
    }

    @Test
    fun `cooking three portions at once takes three times as much`() {
        val uses = FridgeMath.uses(listOf(chicken), portionFactor = 1.0, portions = 3)
        assertEquals(600.0, uses.single().grams, 0.0001)
    }

    @Test
    fun `a factor off the generator's grid is snapped before scaling`() {
        // 1.0482 is not a legal factor; the plan would have stored 1.05.
        val snapped = FridgeMath.uses(listOf(chicken), portionFactor = 1.0482).single().grams
        val legal = FridgeMath.uses(listOf(chicken), portionFactor = 1.05).single().grams
        assertEquals(legal, snapped, 0.0001)
    }

    @Test
    fun `cooking exactly what was bought leaves no ghost row`() {
        // The factor reaches the recipe screen as a Float, so the deduction is
        // never bit-for-bit the amount the shopping list added.
        val bought = mapOf("Chicken" to 200.0 * 1.05)
        val uses = FridgeMath.uses(listOf(chicken), portionFactor = 1.05f.toDouble())
        val left = FridgeMath.applyUses(bought, uses)
        assertFalse("a residue of a millionth of a gram must not survive", left.containsKey("Chicken"))
    }

    @Test
    fun `partial stock ends at nothing left, never at a negative`() {
        val left = FridgeMath.applyUses(mapOf("Chicken" to 100.0), listOf(Use("Chicken", 150.0)))
        assertTrue(left.isEmpty())
    }

    @Test
    fun `cooking an ingredient the fridge never had changes nothing`() {
        val stock = mapOf("Rice" to 500.0)
        assertEquals(stock, FridgeMath.applyUses(stock, listOf(Use("Saffron", 1.0))))
    }

    @Test
    fun `a real leftover stays`() {
        val left = FridgeMath.applyUses(mapOf("Rice" to 500.0), listOf(Use("Rice", 120.0)))
        assertEquals(380.0, left.getValue("Rice"), 0.0001)
    }

    private fun need(id: Long, name: String, vararg uses: Use) =
        FridgeMath.RecipeNeed(id, name, uses.toList())

    @Test
    fun `recipes are ranked by how little is missing`() {
        val recipes = listOf(
            need(1, "Stew", chicken, rice),
            need(2, "Rice bowl", rice),
        )
        val ranked = FridgeMath.rank(recipes, mapOf("Rice" to 500.0))
        assertEquals("Rice bowl", ranked.first().name)
        assertEquals(0, ranked.first().missingCount)
        assertEquals(listOf("Chicken"), ranked[1].missingNames)
    }

    @Test
    fun `a crumb is not having it`() {
        // 3 g of onion must not make every onion recipe cookable.
        val ranked = FridgeMath.rank(listOf(need(1, "Stew", rice)), mapOf("Rice" to 3.0))
        assertEquals(1, ranked.single().missingCount)
    }

    @Test
    fun `exactly enough counts as having it`() {
        val ranked = FridgeMath.rank(listOf(need(1, "Stew", rice)), mapOf("Rice" to 120.0))
        assertEquals(0, ranked.single().missingCount)
    }

    @Test
    fun `equal misses are ordered by name`() {
        val ranked = FridgeMath.rank(
            listOf(need(2, "Borscht", chicken), need(1, "Apple pie", chicken)),
            emptyMap(),
        )
        assertEquals(listOf("Apple pie", "Borscht"), ranked.map { it.name })
    }

    @Test
    fun `what the week already plans sinks below an equally cookable idea`() {
        val recipes = listOf(need(1, "Already planned", rice), need(2, "Something new", rice))
        val ranked = FridgeMath.rank(
            recipes = recipes,
            stock = mapOf("Rice" to 500.0),
            plannedIds = setOf(1L),
        )
        assertEquals(listOf("Something new", "Already planned"), ranked.map { it.name })
    }

    @Test
    fun `being planned never beats actually having the ingredients`() {
        val recipes = listOf(need(1, "Planned but short", chicken), need(2, "New and cookable", rice))
        val ranked = FridgeMath.rank(recipes, mapOf("Rice" to 500.0), plannedIds = setOf(2L))
        assertEquals("New and cookable", ranked.first().name)
    }

    @Test
    fun `an empty fridge misses everything and still ranks`() {
        val ranked = FridgeMath.rank(listOf(need(1, "Stew", chicken, rice)), emptyMap())
        assertEquals(2, ranked.single().missingCount)
    }
}
