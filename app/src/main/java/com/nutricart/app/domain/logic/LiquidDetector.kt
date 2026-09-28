package com.nutricart.app.domain.logic

/**
 * Is this product a drink? Open Food Facts stores every nutriment "per 100 g"
 * even for liquids, where the label really means per 100 ml, and 1 ml of a
 * drink weighs about 1 g. The numbers therefore stay as they are; only the
 * unit the user types and sees changes. The website (web/src/domain/liquid.ts)
 * applies the same rule to the same fields.
 */
object LiquidDetector {

    private val volume = Regex("""(^|[^a-z])\d+([.,]\d+)?\s*(ml|cl|dl|l)(?![a-z])""", RegexOption.IGNORE_CASE)

    fun isLiquid(nutritionDataPer: String?, quantity: String?, servingSize: String?): Boolean {
        if (nutritionDataPer?.trim()?.lowercase() == "100ml") return true
        return listOf(quantity, servingSize).any { it != null && volume.containsMatchIn(it) }
    }
}
