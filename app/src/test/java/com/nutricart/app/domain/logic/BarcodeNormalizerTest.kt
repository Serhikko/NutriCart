package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeNormalizerTest {

    @Test
    fun `an EAN-13 is tried as-is and nothing else`() {
        // Typical UK pack (GS1 prefix 50).
        assertEquals(listOf("5000112637922"), BarcodeNormalizer.candidates("5000112637922"))
    }

    @Test
    fun `a 12-digit UPC-A also tries the zero-padded 13-digit form`() {
        assertEquals(
            listOf("012345678905", "0012345678905"),
            BarcodeNormalizer.candidates("012345678905"),
        )
    }

    @Test
    fun `a 13-digit code with a leading zero also tries the bare 12 digits`() {
        assertEquals(
            listOf("0012345678905", "012345678905"),
            BarcodeNormalizer.candidates("0012345678905"),
        )
    }

    @Test
    fun `an EAN-8 is tried first, then its padded form`() {
        // 9638507 -> check digit 4; starts with 9, so no UPC-E expansion.
        assertEquals(
            listOf("96385074", "0000096385074"),
            BarcodeNormalizer.candidates("96385074"),
        )
    }

    @Test
    fun `a UPC-E read is expanded to UPC-A before padding`() {
        // 01234565 is the textbook UPC-E for UPC-A 012345000065.
        assertEquals(
            listOf("01234565", "012345000065", "0012345000065", "0000001234565"),
            BarcodeNormalizer.candidates("01234565"),
        )
    }

    @Test
    fun `a GTIN-14 also tries the retail code inside it`() {
        assertEquals(
            listOf("15000112637929", "5000112637929"),
            BarcodeNormalizer.candidates("15000112637929"),
        )
    }

    @Test
    fun `whitespace and dashes are ignored, non-digits give nothing`() {
        assertEquals(listOf("5000112637922"), BarcodeNormalizer.candidates(" 5000-1126-37922 "))
        assertTrue(BarcodeNormalizer.candidates("abc").isEmpty())
        assertTrue(BarcodeNormalizer.candidates("").isEmpty())
    }

    @Test
    fun `UPC-E expansion covers every last-digit rule`() {
        // Each input is a valid UPC-E (its check digit is that of the expanded UPC-A).
        assertEquals("012000003455", BarcodeNormalizer.expandUpcE("01234505")) // last digit 0-2
        assertEquals("012300000451", BarcodeNormalizer.expandUpcE("01234531")) // last digit 3
        assertEquals("012340000053", BarcodeNormalizer.expandUpcE("01234543")) // last digit 4
        assertEquals("012345000065", BarcodeNormalizer.expandUpcE("01234565")) // last digit 5-9
    }

    @Test
    fun `UPC-E expansion rejects codes that cannot be UPC-E`() {
        assertNull(BarcodeNormalizer.expandUpcE("96385074")) // number system 9 = EAN-8
        assertNull(BarcodeNormalizer.expandUpcE("0123456"))  // too short
        assertNull(BarcodeNormalizer.expandUpcE("01234564")) // wrong check digit
    }

    @Test
    fun `GTIN check digit`() {
        assertTrue(BarcodeNormalizer.gtinCheckDigitValid("5000112637922"))
        assertTrue(BarcodeNormalizer.gtinCheckDigitValid("012345678905"))
        assertTrue(BarcodeNormalizer.gtinCheckDigitValid("96385074"))
        assertFalse(BarcodeNormalizer.gtinCheckDigitValid("5000112637923"))
        assertFalse(BarcodeNormalizer.gtinCheckDigitValid("12345"))
    }
}
