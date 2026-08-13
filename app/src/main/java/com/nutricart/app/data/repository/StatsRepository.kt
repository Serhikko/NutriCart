package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.FoodContribution
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.RangeNutritionTotals
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WaterRangeStats
import com.nutricart.app.data.local.dao.WorkoutDao
import com.nutricart.app.data.local.entity.DailyActivityEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Top-5 foods per nutrient over a range — the "diet analysis" card. */
data class TopSources(
    val protein: List<FoodContribution>,
    val fat: List<FoodContribution>,
    val carbs: List<FoodContribution>,
    val sugars: List<FoodContribution>,
)

/**
 * Read-only aggregates for the statistics screen. Crosses several tables on
 * purpose — statistics is a VIEW over everything, it owns no data of its own.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val activityDao: ActivityDao,
    private val workoutDao: WorkoutDao,
    private val waterDao: WaterDao,
) {

    /** Eaten kcal per day, keyed by epochDay (days without entries are absent). */
    suspend fun eatenKcalByDay(from: Long, to: Long): Map<Long, Double> =
        foodLogDao.dayKcalBetween(from, to).associate { it.epochDay to it.kcal }

    suspend fun rangeTotals(from: Long, to: Long): RangeNutritionTotals =
        foodLogDao.rangeTotals(from, to)

    suspend fun topSources(from: Long, to: Long): TopSources = TopSources(
        protein = foodLogDao.topProteinSources(from, to),
        fat = foodLogDao.topFatSources(from, to),
        carbs = foodLogDao.topCarbSources(from, to),
        sugars = foodLogDao.topSugarSources(from, to),
    )

    /** Watch data per day — activeKcal feeds each day's historical target. */
    suspend fun activityByDay(from: Long, to: Long): Map<Long, DailyActivityEntity> =
        activityDao.daysBetween(from, to).associateBy { it.epochDay }

    /** Manual workout kcal per day (watch sessions live inside activeKcal). */
    suspend fun manualWorkoutKcalByDay(from: Long, to: Long): Map<Long, Double> =
        workoutDao.manualKcalByDay(from, to).associate { it.epochDay to it.kcal }

    suspend fun waterStats(from: Long, to: Long): WaterRangeStats =
        waterDao.rangeStats(from, to)
}
