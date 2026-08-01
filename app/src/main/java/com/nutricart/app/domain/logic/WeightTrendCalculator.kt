package com.nutricart.app.domain.logic

/**
 * Fits a straight line through weight measurements (least squares — the
 * classic "best fit line" from statistics). The dashboard draws it as the
 * trend and shows the slope as "kg per week".
 */
object WeightTrendCalculator {

    data class Trend(
        val slopeKgPerDay: Double,
        private val interceptKg: Double,
    ) {
        /** The trend line's weight for a given day — used to draw its endpoints. */
        fun weightAt(epochDay: Long): Double = interceptKg + slopeKgPerDay * epochDay

        val slopeKgPerWeek: Double get() = slopeKgPerDay * 7.0
    }

    /**
     * [points] = (epochDay, weightKg), one per day. Returns null when fewer
     * than two DIFFERENT days exist — you cannot draw a line through one point.
     */
    fun calculate(points: List<Pair<Long, Double>>): Trend? {
        if (points.size < 2) return null

        // Work with day offsets from the first point: small numbers keep the
        // floating-point math precise (epoch days are ~20 000).
        val firstDay = points.minOf { it.first }
        val xs = points.map { (it.first - firstDay).toDouble() }
        val ys = points.map { it.second }

        val xMean = xs.average()
        val yMean = ys.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in xs.indices) {
            numerator += (xs[i] - xMean) * (ys[i] - yMean)
            denominator += (xs[i] - xMean) * (xs[i] - xMean)
        }
        if (denominator == 0.0) return null // all points on the same day

        val slope = numerator / denominator
        // Shift the intercept back into real epoch-day space.
        val intercept = yMean - slope * (xMean + firstDay)
        return Trend(slopeKgPerDay = slope, interceptKg = intercept)
    }
}
