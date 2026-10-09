package com.nutricart.app.domain.model

import com.nutricart.app.domain.logic.BarcodeOrigin

/**
 * Everything Open Food Facts knows about one product, even when that is not
 * enough to log it (no name, or a core value missing). A scan of such a
 * product opens the "new food" form with these values filled in, instead of
 * a dead-end "not found".
 *
 * Every number is per 100 g (per 100 ml for a drink) and resolved by the
 * same rules as a complete product; null = OFF doesn't know it.
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
) {
    /** Which GS1 office issued [barcode] (Ukraine / Belarus), else null. */
    val country: BarcodeCountry? get() = BarcodeOrigin.countryOf(barcode)
}
