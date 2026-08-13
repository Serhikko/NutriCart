package com.nutricart.app.domain.logic

/**
 * Pure math for food amounts. Product labels state nutrition per 100 g,
 * the user eats some other amount — this converts between the two.
 */
object FoodMath {

    data class Nutrition(
        val kcal: Double,
        val proteinG: Double,
        val fatG: Double,
        val carbsG: Double,
    )

    /** Nutrition of [grams] of a product whose label values are per 100 g. */
    fun forGrams(
        kcalPer100g: Double,
        proteinPer100g: Double,
        fatPer100g: Double,
        carbsPer100g: Double,
        grams: Double,
    ): Nutrition {
        val factor = grams / 100.0
        return Nutrition(
            kcal = kcalPer100g * factor,
            proteinG = proteinPer100g * factor,
            fatG = fatPer100g * factor,
            carbsG = carbsPer100g * factor,
        )
    }

    /** "2 portions of a 55 g portion" -> 110 g. */
    fun servingsToGrams(servings: Double, servingSizeG: Double): Double =
        servings * servingSizeG

    /**
     * Scales an OPTIONAL per-100g value to the eaten grams. Null passes
     * through untouched: "the label doesn't state fiber" must stay unknown,
     * never become a fake 0 that pollutes day sums.
     */
    fun scalePer100g(valuePer100g: Double?, grams: Double): Double? =
        valuePer100g?.let { it * grams / 100.0 }
}
