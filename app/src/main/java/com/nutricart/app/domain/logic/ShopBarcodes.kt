package com.nutricart.app.domain.logic

/**
 * Which scans are worth asking the Ukrainian shops' catalogue (the zakaz.ua
 * stores API) about, and under which code. The website applies the same
 * rules to the same test vectors.
 */
object ShopBarcodes {

    /**
     * The code the shops' API files products under: the GTIN-14, i.e. the
     * barcode left-padded with zeros to 14 digits. Only ASCII digits of a
     * real barcode length (8, 12, 13 or 14) qualify; anything else is null,
     * so nothing odd ever reaches the request path.
     */
    fun zakazCode(code: String): String? {
        if (code.isEmpty() || !code.all { it in '0'..'9' }) return null
        if (code.length !in setOf(8, 12, 13, 14)) return null
        return code.padStart(14, '0')
    }

    /**
     * False for Belarusian (GS1 481) and UK (500-509) barcodes: the shops
     * list practically none of those (two Belarusian items, about 180 UK
     * non-alcohol ones), so asking would only cost the user seconds. True
     * for Ukrainian codes and for imports (German, Polish, US...), which
     * Ukrainian shops do sell.
     */
    fun shouldAskShops(scannedCode: String): Boolean {
        if (zakazCode(scannedCode) == null) return false
        val prefix = BarcodeOrigin.gs1Prefix(scannedCode) ?: return false
        if (prefix == "481") return false
        return prefix.toInt() !in 500..509
    }
}
