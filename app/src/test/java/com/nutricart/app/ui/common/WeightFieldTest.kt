package com.nutricart.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The weight sheet's field: whatever it prefills or steps to must read back as the same number,
 * in every locale. A locale NumberFormat once wrote Persian and Arabic digits there that the
 * parser could not read, so the prefilled weight showed as an error and Save stayed disabled.
 */
class WeightFieldTest {

    private val delta = 1e-9

    @Test
    fun `English and Ukrainian keep their own decimal point and comma`() {
        assertEquals("78.4", kgFieldText(78.4, Locale.ENGLISH))
        assertEquals("78,4", kgFieldText(78.4, Locale.forLanguageTag("uk")))
    }

    @Test
    fun `Persian and Arabic get ASCII digits and a decimal point`() {
        assertEquals("78.4", kgFieldText(78.4, Locale.forLanguageTag("fa")))
        assertEquals("78.4", kgFieldText(78.4, Locale.forLanguageTag("ar")))
    }

    @Test
    fun `the prefill shows one or two decimals and never groups`() {
        assertEquals("80.0", kgFieldText(80.0, Locale.ENGLISH))
        assertEquals("78.45", kgFieldText(78.45, Locale.ENGLISH))
        assertEquals("78.5", kgFieldText(78.45, Locale.ENGLISH, maxFractionDigits = 1))
        assertEquals("1234.5", kgFieldText(1234.5, Locale.ENGLISH))
    }

    @Test
    fun `every prefill and step reads back in every locale`() {
        val locales = listOf("en", "uk", "de", "fr", "fa", "ar", "hi", "bn", "th-TH-u-nu-thai", "my")
            .map(Locale::forLanguageTag)
        for (locale in locales) {
            for (kg in listOf(30.0, 61.25, 78.4, 99.9, 300.0)) {
                assertEquals("$locale prefill $kg", kg, parseKg(kgFieldText(kg, locale))!!, delta)
            }
            // A step always lands on the 0.1 kg grid.
            for (kg in listOf(30.0, 61.3, 78.5, 99.9, 300.0)) {
                assertEquals("$locale step $kg", kg, parseKg(kgFieldText(kg, locale, maxFractionDigits = 1))!!, delta)
            }
        }
    }

    @Test
    fun `typed text reads with a comma, spaces or another script's digits`() {
        assertEquals(78.4, parseKg("78,4")!!, delta)
        assertEquals(78.4, parseKg(" 78 . 4 ")!!, delta)
        assertEquals(78.4, parseKg("۷۸٫۴")!!, delta)
        assertEquals(78.4, parseKg("٧٨٫٤")!!, delta)
        assertNull(parseKg(""))
        assertNull(parseKg("78,4,1"))
        assertNull(parseKg("kg"))
    }
}
