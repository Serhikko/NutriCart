package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry

/**
 * Which national GS1 office issued a barcode: 482 is GS1 Ukraine, 481 is GS1
 * Belarus. The website (web/src/domain/barcodeOrigin.ts) applies the same rule
 * to the same test vectors.
 *
 * A GS1 prefix says where the NUMBER was registered, not where the food was
 * made (a Ukrainian brand may pack abroad, an importer may register locally),
 * so the screens say "Ukrainian barcode" or "this Ukrainian product", never
 * "made in". The check digit is not verified: this is about origin only.
 */
object BarcodeOrigin {

    private val prefixes = mapOf(
        "482" to BarcodeCountry.UKRAINE,
        "481" to BarcodeCountry.BELARUS,
    )

    fun countryOf(scanned: String): BarcodeCountry? = gs1Prefix(scanned)?.let { prefixes[it] }

    /**
     * The GS1 prefix: the first three digits of the code's 13-digit form, or
     * of an EAN-8 as printed. null for any other length. A 12-digit UPC-A
     * (US/Canada numbering) gets its leading zero, so its prefix starts with
     * 0 and is never 481 or 482.
     */
    fun gs1Prefix(scanned: String): String? {
        // ASCII digits only, like the website's /\D/g.
        val digits = scanned.filter { it in '0'..'9' }
        return when (digits.length) {
            13, 8 -> digits.take(3)
            12 -> "0" + digits.take(2)
            // GTIN-14 wraps the retail code behind a packaging-level digit.
            14 -> digits.substring(1, 4)
            else -> null
        }
    }
}
