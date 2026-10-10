package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same vectors as web/src/domain/__tests__ (barcode origin). */
class BarcodeOriginTest {

    @Test
    fun `GS1 prefix 482 is Ukraine and 481 is Belarus`() {
        assertEquals(BarcodeCountry.UKRAINE, BarcodeOrigin.countryOf("4820024700016"))
        assertEquals(BarcodeCountry.BELARUS, BarcodeOrigin.countryOf("4810268000013"))
    }

    @Test
    fun `EAN-8 uses the same prefixes`() {
        assertEquals(BarcodeCountry.UKRAINE, BarcodeOrigin.countryOf("48212345"))
    }

    @Test
    fun `GTIN-14 drops the packaging digit first`() {
        assertEquals(BarcodeCountry.UKRAINE, BarcodeOrigin.countryOf("14820024700013"))
    }

    @Test
    fun `a 12-digit UPC-A is US and Canada numbering, never Ukraine`() {
        assertNull(BarcodeOrigin.countryOf("482002470001"))
    }

    @Test
    fun `other prefixes and padded codes have no special origin`() {
        assertNull(BarcodeOrigin.countryOf("0482002470001"))
        assertNull(BarcodeOrigin.countryOf("5000112637922"))
        assertNull(BarcodeOrigin.countryOf("4800000000000"))
        assertNull(BarcodeOrigin.countryOf("4830000000000"))
    }

    @Test
    fun `only the digits count, and no digits is no origin`() {
        assertEquals(BarcodeCountry.UKRAINE, BarcodeOrigin.countryOf("abc 482 0024 700016"))
        assertNull(BarcodeOrigin.countryOf(""))
    }

    @Test
    fun `the GS1 prefix comes from the 13-digit form, or from an EAN-8 as printed`() {
        assertEquals("482", BarcodeOrigin.gs1Prefix("4823090100292"))
        assertEquals("401", BarcodeOrigin.gs1Prefix("40111445"))
        assertEquals("001", BarcodeOrigin.gs1Prefix("012345678905")) // UPC-A gets its zero
        assertEquals("482", BarcodeOrigin.gs1Prefix("14820024700013")) // GTIN-14
        assertNull(BarcodeOrigin.gs1Prefix("123"))
        assertNull(BarcodeOrigin.gs1Prefix(""))
    }
}
