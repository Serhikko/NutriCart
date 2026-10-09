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

    fun countryOf(scanned: String): BarcodeCountry? {
        // ASCII digits only, like the website's /\D/g.
        val digits = scanned.filter { it in '0'..'9' }
        // GTIN-14 wraps the retail code behind a packaging-level digit.
        val code = if (digits.length == 14) digits.substring(1) else digits
        // EAN-13 and EAN-8 only; a 12-digit UPC-A is US/Canada numbering.
        if (code.length != 13 && code.length != 8) return null
        return prefixes[code.take(3)]
    }
}
