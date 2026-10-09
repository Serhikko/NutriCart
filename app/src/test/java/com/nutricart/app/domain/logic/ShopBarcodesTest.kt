package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same vectors as the website's tests (Ukrainian shops' barcodes). */
class ShopBarcodesTest {

    @Test
    fun `real barcode lengths are zero-padded to GTIN-14`() {
        assertEquals("04823090100292", ShopBarcodes.zakazCode("4823090100292"))
        assertEquals("00000040111445", ShopBarcodes.zakazCode("40111445"))
        assertEquals("00012345678905", ShopBarcodes.zakazCode("012345678905"))
        assertEquals("04823090100292", ShopBarcodes.zakazCode("04823090100292"))
    }

    @Test
    fun `anything else has no shop code`() {
        assertNull(ShopBarcodes.zakazCode("123"))
        assertNull(ShopBarcodes.zakazCode(""))
        assertNull(ShopBarcodes.zakazCode("123456789012345"))
        // Never anything but digits in the request path.
        assertNull(ShopBarcodes.zakazCode("48230901/0292"))
    }

    @Test
    fun `Ukrainian codes and imports are worth asking the shops about`() {
        assertTrue(ShopBarcodes.shouldAskShops("4823090100292"))
        assertTrue(ShopBarcodes.shouldAskShops("5101234567890")) // 510: not a UK prefix
        assertTrue(ShopBarcodes.shouldAskShops("4006381333931")) // Germany
        assertTrue(ShopBarcodes.shouldAskShops("40111445")) // EAN-8
        assertTrue(ShopBarcodes.shouldAskShops("012345678905")) // UPC-A
    }

    @Test
    fun `Belarusian and UK codes, and non-barcodes, are not`() {
        assertFalse(ShopBarcodes.shouldAskShops("4810000000001"))
        assertFalse(ShopBarcodes.shouldAskShops("5000000000001"))
        assertFalse(ShopBarcodes.shouldAskShops("5091234567890"))
        assertFalse(ShopBarcodes.shouldAskShops("50123452")) // UK EAN-8
        assertFalse(ShopBarcodes.shouldAskShops("123"))
    }
}
