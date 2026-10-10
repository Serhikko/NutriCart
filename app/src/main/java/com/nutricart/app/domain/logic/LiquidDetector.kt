package com.nutricart.app.domain.logic

/**
 * Is this product a drink? Open Food Facts stores every nutriment "per 100 g"
 * even for liquids, where the label really means per 100 ml, and 1 ml of a
 * drink weighs about 1 g. The numbers therefore stay as they are; only the
 * unit the user types and sees changes. The website (web/src/domain/liquid.ts)
 * applies the same rule to the same fields.
 *
 * Ukrainian and Belarusian packs write the size in Cyrillic ("500 мл",
 * "0,5 л", "1 літр", "1 литр"). The boundaries use the Unicode letter class:
 * a unit that is only the start of a word ("1 large", "1 лист", "3 ложки")
 * does not count, nor does a number right after a letter ("x2l"). The text
 * is lower-cased first ("1,5 Л"), as on the website.
 */
object LiquidDetector {

    // [\s\p{Z}\uFEFF] is exactly JavaScript's \s: Java's \s alone misses the
    // no-break space that some packs put between the number and the unit.
    private val volume = Regex(
        """(^|[^\p{L}])\d+([.,]\d+)?[\s\p{Z}\uFEFF]*(ml|cl|dl|l|мл|л|літр\p{L}*|литр\p{L}*)(?!\p{L})"""
    )

    fun isLiquid(nutritionDataPer: String?, quantity: String?, servingSize: String?): Boolean {
        if (nutritionDataPer?.trim()?.lowercase() == "100ml") return true
        return listOf(quantity, servingSize).any { it != null && volume.containsMatchIn(it.lowercase()) }
    }
}
