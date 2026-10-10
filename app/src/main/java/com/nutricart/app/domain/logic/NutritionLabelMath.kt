package com.nutricart.app.domain.logic

/**
 * Conversions between the ways a food label (or a community database) can
 * state the same nutrition facts. Pure math, no knowledge of any API.
 */
object NutritionLabelMath {

    /** Thermochemical calorie: the factor EU/UK labels use between kJ and kcal. */
    const val KJ_PER_KCAL = 4.184

    fun kcalFromKj(kj: Double): Double = kj / KJ_PER_KCAL

    /**
     * Energy from the Atwater factors (4 / 9 / 4 kcal per gram). A last
     * resort when a source states the macros but no energy value at all.
     */
    fun kcalFromMacros(proteinG: Double, fatG: Double, carbsG: Double): Double =
        proteinG * 4.0 + fatG * 9.0 + carbsG * 4.0

    /**
     * A per-serving value rescaled to per 100 g. null when the serving size
     * is unknown or nonsense — a bad portion must not invent per-100g data.
     */
    fun per100gFromServing(valuePerServing: Double?, servingSizeG: Double?): Double? {
        if (valuePerServing == null || servingSizeG == null || servingSizeG <= 0.0) return null
        return valuePerServing * 100.0 / servingSizeG
    }

    /**
     * The first usable energy value, in kcal per 100 g, from what a label
     * offers — in order of trust: kcal as stated, kJ converted, a per-serving
     * value rescaled, and finally the macros. null when nothing is known.
     */
    fun resolveKcalPer100g(
        kcalPer100g: Double?,
        kjPer100g: Double?,
        kcalPerServing: Double?,
        kjPerServing: Double?,
        servingSizeG: Double?,
        proteinPer100g: Double?,
        fatPer100g: Double?,
        carbsPer100g: Double?,
    ): Double? {
        kcalPer100g?.let { return it }
        kjPer100g?.let { return kcalFromKj(it) }
        per100gFromServing(kcalPerServing, servingSizeG)?.let { return it }
        per100gFromServing(kjPerServing, servingSizeG)?.let { return kcalFromKj(it) }
        if (proteinPer100g != null && fatPer100g != null && carbsPer100g != null) {
            return kcalFromMacros(proteinPer100g, fatPer100g, carbsPer100g)
        }
        return null
    }
}
