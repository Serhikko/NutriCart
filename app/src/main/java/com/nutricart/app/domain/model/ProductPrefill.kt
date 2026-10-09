package com.nutricart.app.domain.model

import com.nutricart.app.domain.logic.BarcodeOrigin

/**
 * Everything Open Food Facts (or a Ukrainian shop's catalogue) knows about
 * one product, even when that is not enough to log it (no name, or a core
 * value missing). A scan of such a product opens the "new food" form with
 * these values filled in, instead of a dead-end "not found".
 *
 * Every number is per 100 g (per 100 ml for a drink); null = the source
 * doesn't know it. From Open Food Facts a number is resolved by the same
 * rules as a complete product, or else is OFF's estimate from the
 * ingredients; the user confirms the form either way.
 */
data class ProductPrefill(
    /** The product's code (or the looked-up code when OFF gave none). */
    val barcode: String,
    val name: String?,
    val brand: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val fatPer100g: Double?,
    val carbsPer100g: Double?,
    val servingSizeG: Double?,
    val isLiquid: Boolean,
    val fiberPer100g: Double? = null,
    val sugarsPer100g: Double? = null,
    val saltPer100g: Double? = null,
    val saturatedFatPer100g: Double? = null,
    /**
     * A core value (energy, protein, fat or carbs) is Open Food Facts'
     * estimate from the ingredients, not a stated one. With [hasCoreValues]
     * the form asks the user to check the numbers rather than fill gaps.
     */
    val estimated: Boolean = false,
) {
    /** Which GS1 office issued [barcode] (Ukraine / Belarus), else null. */
    val country: BarcodeCountry? get() = BarcodeOrigin.countryOf(barcode)

    /**
     * All four core values are filled in. In a prefill (a product that was
     * not usable as is) the numbers then mostly need checking rather than
     * completing: OFF's estimates ([estimated]), or shop values that don't
     * add up.
     */
    val hasCoreValues: Boolean
        get() = kcalPer100g != null && proteinPer100g != null && fatPer100g != null && carbsPer100g != null

    /** Any name or core value at all; an OFF record with neither is a bare stub. */
    val knowsAnything: Boolean
        get() = name != null || kcalPer100g != null || proteinPer100g != null ||
            fatPer100g != null || carbsPer100g != null
}
