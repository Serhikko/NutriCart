package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLabelMathTest {

    private val delta = 0.01

    @Test
    fun `kJ to kcal uses the label factor`() {
        // A UK pack: 1046 kJ printed next to 250 kcal.
        assertEquals(250.0, NutritionLabelMath.kcalFromKj(1046.0), delta)
    }

    @Test
    fun `per-serving values rescale to per 100 g`() {
        // 30 g portion with 120 kcal -> 400 kcal per 100 g.
        assertEquals(400.0, NutritionLabelMath.per100gFromServing(120.0, 30.0)!!, delta)
        assertNull(NutritionLabelMath.per100gFromServing(120.0, null))
        assertNull(NutritionLabelMath.per100gFromServing(120.0, 0.0))
        assertNull(NutritionLabelMath.per100gFromServing(null, 30.0))
    }

    @Test
    fun `energy resolution prefers kcal, then kJ, then serving, then macros`() {
        fun resolve(
            kcal: Double? = null, kj: Double? = null, kcalServing: Double? = null,
            kjServing: Double? = null, serving: Double? = null,
            p: Double? = null, f: Double? = null, c: Double? = null,
        ) = NutritionLabelMath.resolveKcalPer100g(kcal, kj, kcalServing, kjServing, serving, p, f, c)

        assertEquals(250.0, resolve(kcal = 250.0, kj = 9999.0)!!, delta)
        assertEquals(250.0, resolve(kj = 1046.0)!!, delta)
        assertEquals(400.0, resolve(kcalServing = 120.0, serving = 30.0)!!, delta)
        assertEquals(250.0, resolve(kjServing = 313.8, serving = 30.0)!!, delta)
        // 10 g protein, 5 g fat, 40 g carbs -> 40 + 45 + 160 = 245 kcal.
        assertEquals(245.0, resolve(p = 10.0, f = 5.0, c = 40.0)!!, delta)
        assertNull(resolve(p = 10.0, f = 5.0)) // one macro missing: no guess
        assertNull(resolve())
    }
}
