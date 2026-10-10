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

    @Test
    fun `Cyrillic volumes on Ukrainian and Belarusian packs are drinks`() {
        for (size in listOf("500 мл", "0,5 л", "1л", "1,5 Л", "2 літри", "1 литр", "330 ML")) {
            assertTrue(size, LiquidDetector.isLiquid(null, size, null))
        }
        // The serving size counts the same way as the pack size.
        assertTrue(LiquidDetector.isLiquid(null, null, "1 склянка (250 мл)"))
    }

    @Test
    fun `Cyrillic grams, pieces and words that only start like a unit are food`() {
        for (size in listOf("450 г", "1 лист", "3 ложки", "12 шт", "5 lb", "1 large")) {
            assertFalse(size, LiquidDetector.isLiquid(null, size, null))
        }
    }

    @Test
    fun `a number right after a letter is not a volume`() {
        assertFalse(LiquidDetector.isLiquid(null, "x2l", null))
        assertFalse(LiquidDetector.isLiquid(null, "ф2л", null))
    }
}
