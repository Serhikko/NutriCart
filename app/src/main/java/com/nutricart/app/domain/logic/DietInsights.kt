package com.nutricart.app.domain.logic

/**
 * The "gaps and excesses" lines of the diet analysis: which averages fall
 * short of their targets and which exceed their limits. Pure Kotlin.
 *
 * Thresholds mirror NutrientTargets: "short" = below 90% of a reach-target
 * (the same line where targetState stops being NEUTRAL), "excess" = above a
 * stay-under limit (where limitState stops being GOOD).
 */
object DietInsights {

    enum class Kind { PROTEIN_LOW, FIBER_LOW, SUGAR_HIGH, SALT_HIGH, SAT_FAT_HIGH }

    /** One insight: the average that fired and the number it is measured against. */
    data class Insight(val kind: Kind, val avg: Double, val reference: Int)

    /**
     * Averages arrive UNROUNDED on purpose: rounding 5.4 g of salt down to 5
     * before comparing with the 5 g limit would print "all good" while the
     * dashboard flags the same intake as over — review-caught contradiction.
     */
    fun compute(
        avgProteinG: Double,
        proteinTargetG: Int,
        avgFiberG: Double,
        fiberTargetG: Int,
        avgSugarsG: Double,
        sugarLimitG: Int,
        avgSaltG: Double,
        saltLimitG: Int,
        avgSatFatG: Double,
        satFatLimitG: Int,
    ): List<Insight> = buildList {
        fun short(avg: Double, target: Int, kind: Kind) {
            if (target > 0 && avg < target * 0.9) add(Insight(kind, avg, target))
        }
        fun excess(avg: Double, limit: Int, kind: Kind) {
            if (limit > 0 && avg > limit) add(Insight(kind, avg, limit))
        }
        short(avgProteinG, proteinTargetG, Kind.PROTEIN_LOW)
        short(avgFiberG, fiberTargetG, Kind.FIBER_LOW)
        excess(avgSugarsG, sugarLimitG, Kind.SUGAR_HIGH)
        excess(avgSaltG, saltLimitG, Kind.SALT_HIGH)
        excess(avgSatFatG, satFatLimitG, Kind.SAT_FAT_HIGH)
    }
}
