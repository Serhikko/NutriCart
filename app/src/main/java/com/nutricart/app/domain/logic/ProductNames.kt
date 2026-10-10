package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry

/**
 * Picks a product's display name from the many name fields Open Food Facts
 * keeps. Ukrainian and Belarusian products often have a blank product_name
 * and the real name only in product_name_uk / _ru / _be, or only a generic
 * name; reading product_name and product_name_en alone dropped them as
 * "nameless", so the scanner said "not found".
 *
 * The language order follows the barcode's origin, NEVER the app's UI
 * language: the result is cached on the phone and must not change when the
 * user switches languages. The website (web/src/domain/openFoodFacts.ts)
 * applies the same rule to the same test vectors.
 */
object ProductNames {

    /** The OFF language suffixes, in the order to try for a code of [country]. */
    fun languageOrder(country: BarcodeCountry?): List<String> = when (country) {
        BarcodeCountry.UKRAINE -> listOf("uk", "ru", "en", "be")
        BarcodeCountry.BELARUS -> listOf("be", "ru", "en", "uk")
        // English first keeps the old behaviour for UK and other products.
        null -> listOf("en", "uk", "ru", "be")
    }

    /**
     * The first non-blank of: [productName], then product_name_<lang> in the
     * order for [code]'s origin, then [genericName], then generic_name_<lang>
     * in the same order. null when the product has no name at all.
     *
     * [productNameIn] / [genericNameIn] map a language suffix ("uk") to that
     * field's value.
     */
    fun resolve(
        code: String?,
        productName: String?,
        productNameIn: (String) -> String?,
        genericName: String?,
        genericNameIn: (String) -> String?,
    ): String? {
        val langs = languageOrder(code?.let { BarcodeOrigin.countryOf(it) })
        clean(productName)?.let { return it }
        for (lang in langs) clean(productNameIn(lang))?.let { return it }
        clean(genericName)?.let { return it }
        for (lang in langs) clean(genericNameIn(lang))?.let { return it }
        return null
    }

    /**
     * Whitespace runs (spaces, tabs, newlines, no-break spaces) become one
     * space, the ends are trimmed; blank -> null. The character class is
     * exactly JavaScript's \s, so the website cleans names identically.
     */
    fun clean(raw: String?): String? =
        raw?.replace(whitespace, " ")?.trim(' ')?.takeIf { it.isNotEmpty() }

    private val whitespace = Regex("""[\s\p{Z}\uFEFF]+""")
}
