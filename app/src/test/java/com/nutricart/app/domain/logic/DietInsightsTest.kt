package com.nutricart.app.domain.logic

import com.nutricart.app.domain.logic.DietInsights.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DietInsightsTest {

    private fun compute(
        protein: Double = 120.0,
        proteinTarget: Int = 120,
        fiber: Double = 28.0,
        fiberTarget: Int = 28,
        sugars: Double = 40.0,
        sugarLimit: Int = 50,
        salt: Double = 4.0,
        saltLimit: Int = 5,
        satFat: Double = 20.0,
        satFatLimit: Int = 22,
    ) = DietInsights.compute(
        protein, proteinTarget, fiber, fiberTarget,
        sugars, sugarLimit, salt, saltLimit, satFat, satFatLimit,
    )

    @Test
    fun `everything on target gives no insights`() {
        assertTrue(compute().isEmpty())
    }

    @Test
    fun `low protein fires below 90 percent`() {
        val insights = compute(protein = 100.0, proteinTarget = 120) // 83%
        assertEquals(listOf(Kind.PROTEIN_LOW), insights.map { it.kind })
        assertEquals(100.0, insights.single().avg, 0.001)
        assertEquals(120, insights.single().reference)
    }

    @Test
    fun `exactly 90 percent of a target does not fire`() {
        assertTrue(compute(protein = 108.0, proteinTarget = 120).isEmpty())
    }

    @Test
    fun `sugar above the limit fires, at the limit does not`() {
        assertTrue(compute(sugars = 50.0, sugarLimit = 50).isEmpty())
        assertEquals(
            listOf(Kind.SUGAR_HIGH),
            compute(sugars = 51.0, sugarLimit = 50).map { it.kind },
        )
    }

    @Test
    fun `a fractional overshoot is NOT swallowed by rounding`() {
        // 5.4 g of salt vs the 5 g limit: the dashboard shows WARN for this
        // intake, so the stats verdict must fire too (review-caught bug).
        assertEquals(
            listOf(Kind.SALT_HIGH),
            compute(salt = 5.4, saltLimit = 5).map { it.kind },
        )
    }

    @Test
    fun `several problems fire together in a stable order`() {
        val insights = compute(
            protein = 60.0, proteinTarget = 120,
            fiber = 10.0, fiberTarget = 28,
            salt = 8.0, saltLimit = 5,
        )
        assertEquals(
            listOf(Kind.PROTEIN_LOW, Kind.FIBER_LOW, Kind.SALT_HIGH),
            insights.map { it.kind },
        )
    }

    @Test
    fun `zero targets give no verdicts`() {
        assertTrue(
            compute(
                protein = 0.0, proteinTarget = 0,
                fiber = 0.0, fiberTarget = 0,
                sugars = 99.0, sugarLimit = 0,
            ).isEmpty()
        )
    }
}
