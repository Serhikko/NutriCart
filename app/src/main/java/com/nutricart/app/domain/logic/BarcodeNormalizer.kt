package com.nutricart.app.domain.logic

/**
 * Turns whatever the scanner read into the list of codes worth asking Open
 * Food Facts about, most likely first.
 *
 * Why this exists: a scanner reports the digits printed on the pack, but the
 * database stores ONE canonical form per product. UK and EU packs are mostly
 * EAN-13 and match as-is — but imported goods carry 12-digit UPC-A codes,
 * which Open Food Facts keeps as 13 digits with a leading zero; small packs
 * (chewing gum, spices) use EAN-8; and some US imports print the compressed
 * 8-digit UPC-E that must be expanded to UPC-A first. Before this, every one
 * of those scanned fine and then came back "product not found".
 */
object BarcodeNormalizer {

    /**
     * Candidate lookup codes for [raw], deduplicated, in the order to try.
     * Empty when the input holds no digits at all.
     */
    fun candidates(raw: String): List<String> {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        val out = mutableListOf(digits)
        when (digits.length) {
            // UPC-A: Open Food Facts stores it zero-padded to 13.
            12 -> out += "0$digits"
            // A 13-digit code with a leading zero is usually a padded UPC-A;
            // some entries were created with the bare 12 digits.
            13 -> if (digits.startsWith("0")) out += digits.substring(1)
            8 -> {
                // EAN-8 is looked up as-is (first), but an 8-digit read that
                // starts with 0 or 1 may be UPC-E — expand it, then pad.
                expandUpcE(digits)?.let { upcA ->
                    out += upcA
                    out += "0$upcA"
                }
                // Some products were entered zero-padded to 13 digits.
                out += digits.padStart(13, '0')
            }
            // GTIN-14 (outer cases) wraps a 13-digit code after a leading
            // packaging-level digit; the retail code inside is what OFF knows.
            14 -> out += digits.substring(1)
        }
        return out.distinct()
    }

    /**
     * UPC-E -> UPC-A. Input is the 8-digit form: number system (0 or 1),
     * six data digits, check digit. Returns null when the input is not a
     * valid UPC-E (wrong length, wrong number system, or the expanded code's
     * check digit does not match — which also rules out most EAN-8 codes).
     */
    fun expandUpcE(code: String): String? {
        if (code.length != 8 || !code.all { it.isDigit() }) return null
        val ns = code[0]
        if (ns != '0' && ns != '1') return null
        val d = code.substring(1, 7)
        val check = code[7]
        val body = when (val last = d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}$last" + "0000" + "${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}" + "00000" + "${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}" + "00000" + "${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}" + "0000" + last
        }
        val upcA = "$ns$body$check"
        return upcA.takeIf { gtinCheckDigitValid(it) }
    }

    /** Standard GTIN modulo-10 check for 8, 12, 13 or 14-digit codes. */
    fun gtinCheckDigitValid(code: String): Boolean {
        if (code.length !in setOf(8, 12, 13, 14) || !code.all { it.isDigit() }) return false
        val digits = code.map { it - '0' }
        // Weights alternate 3,1,3,1… starting from the digit next to the check digit.
        var sum = 0
        for (i in 0 until digits.size - 1) {
            val fromRight = digits.size - 2 - i
            sum += digits[i] * if (fromRight % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10 == digits.last()
    }
}
