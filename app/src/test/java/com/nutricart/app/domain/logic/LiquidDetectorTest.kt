package com.nutricart.app.domain.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same vectors as web/src/domain/__tests__/liquid.test.ts. */
class LiquidDetectorTest {

    @Test
    fun `nutrition per 100 ml is a drink`() {
        assertTrue(LiquidDetector.isLiquid("100ml", null, null))
    }

    @Test
    fun `a volume in the quantity or serving size is a drink`() {
        assertTrue(LiquidDetector.isLiquid("100g", "500 ml", null))
        assertTrue(LiquidDetector.isLiquid(null, "50cl", null))
        assertTrue(LiquidDetector.isLiquid(null, "1,5 L", null))
        assertTrue(LiquidDetector.isLiquid(null, null, "1 can (330 ml)"))
    }

    @Test
    fun `grams, pieces and blanks are food`() {
        assertFalse(LiquidDetector.isLiquid("100g", "500 g", "30 g"))
        assertFalse(LiquidDetector.isLiquid(null, "6 pcs", ""))
        assertFalse(LiquidDetector.isLiquid(null, null, null))
        assertFalse(LiquidDetector.isLiquid(null, "small", "medium"))
    }
}
