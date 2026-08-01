package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.FoodMath
import com.nutricart.app.domain.model.MealSlot
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The food diary: what was eaten, when, and the day's running totals. */
@Singleton
class DiaryRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
) {

    fun observeDay(epochDay: Long): Flow<List<FoodLogEntryEntity>> =
        foodLogDao.observeDay(epochDay)

    fun observeDayTotals(epochDay: Long): Flow<DayNutritionTotals> =
        foodLogDao.observeDayTotals(epochDay)

    /** Days with at least one entry — input for the dashboard streak. */
    fun observeLoggedDays(): Flow<List<Long>> = foodLogDao.observeLoggedDays()

    /**
     * Logs [grams] of a product into a meal. The nutrition numbers are computed
     * HERE, once, and stored as a snapshot — the diary never changes later.
     * [servings] is only remembered for display ("2 portions"), grams stays
     * the source of truth.
     */
    suspend fun logProduct(
        product: FoodProductEntity,
        grams: Double,
        servings: Double?,
        meal: MealSlot,
        epochDay: Long,
    ) {
        val nutrition = FoodMath.forGrams(
            kcalPer100g = product.kcalPer100g,
            proteinPer100g = product.proteinPer100g,
            fatPer100g = product.fatPer100g,
            carbsPer100g = product.carbsPer100g,
            grams = grams,
        )
        foodLogDao.insert(
            FoodLogEntryEntity(
                epochDay = epochDay,
                meal = meal,
                productId = product.id,
                name = product.name,
                grams = grams,
                servings = servings,
                kcal = nutrition.kcal,
                proteinG = nutrition.proteinG,
                fatG = nutrition.fatG,
                carbsG = nutrition.carbsG,
                loggedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(entry: FoodLogEntryEntity) = foodLogDao.delete(entry)

    /**
     * Logs a ready nutrition snapshot without a product — used by the meal
     * plan's "add to diary" (the recipe name + already-scaled numbers go in).
     */
    suspend fun logSnapshot(
        name: String,
        kcal: Double,
        proteinG: Double,
        fatG: Double,
        carbsG: Double,
        meal: MealSlot,
        epochDay: Long,
    ) {
        foodLogDao.insert(
            FoodLogEntryEntity(
                epochDay = epochDay,
                meal = meal,
                productId = null,
                name = name,
                grams = null,
                servings = null,
                kcal = kcal,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                loggedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }
}
