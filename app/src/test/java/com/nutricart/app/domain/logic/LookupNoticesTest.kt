package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.ShopsStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same vectors as the website's tests (the not-found message). */
class LookupNoticesTest {

    private val countries = listOf(BarcodeCountry.UKRAINE, BarcodeCountry.BELARUS, null)

    @Test
    fun `Open Food Facts down wins, naming the shops only when they answered`() {
        for (country in countries) {
            assertEquals(LookupNotice.OFF_DOWN_SHOPS_MISS, lookupNotice(country, true, ShopsStatus.MISS))
            assertEquals(LookupNotice.OFF_DOWN, lookupNotice(country, true, ShopsStatus.NOT_ASKED))
            assertEquals(LookupNotice.OFF_DOWN, lookupNotice(country, true, ShopsStatus.UNAVAILABLE))
        }
    }

    @Test
    fun `both sources answered - not found in either`() {
        assertEquals(
            LookupNotice.NOT_FOUND_UKRAINE_SHOPS,
            lookupNotice(BarcodeCountry.UKRAINE, false, ShopsStatus.MISS),
        )
        assertEquals(LookupNotice.NOT_FOUND_OTHER_SHOPS, lookupNotice(null, false, ShopsStatus.MISS))
        assertEquals(
            LookupNotice.NOT_FOUND_OTHER_SHOPS,
            lookupNotice(BarcodeCountry.BELARUS, false, ShopsStatus.MISS),
        )
    }

    @Test
    fun `the shops didn't answer - only Open Food Facts is named`() {
        for (country in countries) {
            assertEquals(
                LookupNotice.NOT_FOUND_SHOPS_DOWN,
                lookupNotice(country, false, ShopsStatus.UNAVAILABLE),
            )
        }
    }

    @Test
    fun `the shops weren't asked - the message by the barcode's origin`() {
        assertEquals(
            LookupNotice.NOT_FOUND_UKRAINE,
            lookupNotice(BarcodeCountry.UKRAINE, false, ShopsStatus.NOT_ASKED),
        )
        assertEquals(
            LookupNotice.NOT_FOUND_BELARUS,
            lookupNotice(BarcodeCountry.BELARUS, false, ShopsStatus.NOT_ASKED),
        )
        assertEquals(LookupNotice.NOT_FOUND_OTHER, lookupNotice(null, false, ShopsStatus.NOT_ASKED))
    }
}
