package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure name rule. The shared vectors, decoded from real OFF JSON, live
 * in ProductDtoMappingTest (same vectors as the website's tests).
 */
class ProductNamesTest {

    private fun resolve(
        code: String?,
        productName: String? = null,
        names: Map<String, String> = emptyMap(),
        genericName: String? = null,
        generics: Map<String, String> = emptyMap(),
    ): String? = ProductNames.resolve(code, productName, { names[it] }, genericName, { generics[it] })

    @Test
    fun `the language order follows the barcode's origin`() {
        assertEquals(listOf("uk", "ru", "en", "be"), ProductNames.languageOrder(BarcodeCountry.UKRAINE))
        assertEquals(listOf("be", "ru", "en", "uk"), ProductNames.languageOrder(BarcodeCountry.BELARUS))
        assertEquals(listOf("en", "uk", "ru", "be"), ProductNames.languageOrder(null))
    }

    @Test
    fun `every product name beats every generic name`() {
        val name = resolve(
            code = "4820024700016",
            names = mapOf("be" to "Хлеб"),
            genericName = "Bread",
            generics = mapOf("uk" to "Хліб"),
        )
        assertEquals("Хлеб", name)
    }

    @Test
    fun `no name anywhere is no name`() {
        assertNull(resolve(code = "4820024700016", productName = " ", names = mapOf("uk" to "\t")))
        assertNull(resolve(code = null))
    }

    @Test
    fun `cleaning collapses every kind of whitespace, like JavaScript's s`() {
        assertEquals("Хліб житній", ProductNames.clean("  Хліб\n\t житній  "))
        // No-break and other Unicode spaces count as whitespace too.
        assertEquals("Сир 9%", ProductNames.clean("\u00A0Сир\u00A09%\u2009"))
        assertNull(ProductNames.clean(" \u00A0\n "))
        assertNull(ProductNames.clean(null))
    }
}
