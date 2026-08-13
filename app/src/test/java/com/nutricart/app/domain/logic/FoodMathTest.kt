package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodMathTest {

    private val delta = 0.001

    @Test
    fun `80 grams of a 250 kcal product is 200 kcal`() {
        // Label per 100 g: 250 kcal, 10 g protein, 5 g fat, 40 g carbs.
        val n = FoodMath.forGrams(250.0, 10.0, 5.0, 40.0, grams = 80.0)
        assertEquals(200.0, n.kcal, delta)
        assertEquals(8.0, n.proteinG, delta)
        assertEquals(4.0, n.fatG, delta)
        assertEquals(32.0, n.carbsG, delta)
    }

    @Test
    fun `100 grams returns the label values unchanged`() {
        val n = FoodMath.forGrams(365.0, 13.0, 6.6, 61.0, grams = 100.0)
        assertEquals(365.0, n.kcal, delta)
        assertEquals(13.0, n.proteinG, delta)
    }

    @Test
    fun `zero grams is zero everything`() {
        val n = FoodMath.forGrams(250.0, 10.0, 5.0, 40.0, grams = 0.0)
        assertEquals(0.0, n.kcal, delta)
        assertEquals(0.0, n.proteinG, delta)
        assertEquals(0.0, n.fatG, delta)
        assertEquals(0.0, n.carbsG, delta)
    }

    @Test
    fun `two portions of 55 grams is 110 grams`() {
        assertEquals(110.0, FoodMath.servingsToGrams(2.0, 55.0), delta)
    }

    @Test
    fun `half a portion works too`() {
        assertEquals(27.5, FoodMath.servingsToGrams(0.5, 55.0), delta)
    }

    @Test
    fun `optional nutrient scales like the label says`() {
        // 3 g fiber per 100 g, eaten 250 g -> 7.5 g.
        assertEquals(7.5, FoodMath.scalePer100g(3.0, 250.0)!!, delta)
    }

    @Test
    fun `unknown optional nutrient stays unknown`() {
        // null must never turn into 0 — 0 would pollute the day's sums.
        assertEquals(null, FoodMath.scalePer100g(null, 250.0))
    }

    @Test
    fun `optional nutrient of zero grams is zero, not null`() {
        assertEquals(0.0, FoodMath.scalePer100g(3.0, 0.0)!!, delta)
    }
}
