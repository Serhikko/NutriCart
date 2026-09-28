package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.local.entity.SavedMealItemEntity
import com.nutricart.app.domain.logic.FoodMath
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.partner.PartnerScheduling
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The food diary: what was eaten, when, and the day's running totals.
 *
 * Every write path ends with [PartnerScheduling.onMealLogged]: it lives here
 * and not in the four ViewModels that log food, so a fifth way to log added
 * later can never forget to tell the partner. The scheduler decides whether
 * anything is actually sent.
 */
@Singleton
class DiaryRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val partnerScheduling: PartnerScheduling,
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
        foodLogDao.insert(
            entryFor(product, grams, servings, meal, epochDay, System.currentTimeMillis())
        )
        partnerScheduling.onMealLogged(meal, epochDay)
    }

    /** The one place that turns a product + grams into a diary snapshot row. */
    private fun entryFor(
        product: FoodProductEntity,
        grams: Double,
        servings: Double?,
        meal: MealSlot,
        epochDay: Long,
        loggedAtEpochMillis: Long,
    ): FoodLogEntryEntity {
        val nutrition = FoodMath.forGrams(
            kcalPer100g = product.kcalPer100g,
            proteinPer100g = product.proteinPer100g,
            fatPer100g = product.fatPer100g,
            carbsPer100g = product.carbsPer100g,
            grams = grams,
        )
        return FoodLogEntryEntity(
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
            // Optional detail nutrients: null stays null ("label doesn't say").
            fiberG = FoodMath.scalePer100g(product.fiberPer100g, grams),
            sugarsG = FoodMath.scalePer100g(product.sugarsPer100g, grams),
            saltG = FoodMath.scalePer100g(product.saltPer100g, grams),
            saturatedFatG = FoodMath.scalePer100g(product.saturatedFatPer100g, grams),
            loggedAtEpochMillis = loggedAtEpochMillis,
        )
    }

    suspend fun delete(entry: FoodLogEntryEntity) = foodLogDao.delete(entry)

    /**
     * Logs every item of a saved meal into one diary section. The items are
     * already snapshots — they are copied as-is, no recomputation, so the
     * diary shows exactly what the meal contained when it was saved.
     * insertAll = one transaction: the meal can never be half-logged.
     */
    suspend fun logSavedMealItems(
        items: List<SavedMealItemEntity>,
        meal: MealSlot,
        epochDay: Long,
    ) {
        val now = System.currentTimeMillis()
        foodLogDao.insertAll(
            items.map { item ->
                FoodLogEntryEntity(
                    epochDay = epochDay,
                    meal = meal,
                    productId = item.productId,
                    name = item.name,
                    grams = item.grams,
                    servings = item.servings,
                    kcal = item.kcal,
                    proteinG = item.proteinG,
                    fatG = item.fatG,
                    carbsG = item.carbsG,
                    fiberG = item.fiberG,
                    sugarsG = item.sugarsG,
                    saltG = item.saltG,
                    saturatedFatG = item.saturatedFatG,
                    loggedAtEpochMillis = now,
                )
            }
        )
        partnerScheduling.onMealLogged(meal, epochDay)
    }

    /**
     * Logs several products at once (the multi-add basket) — same math as
     * logProduct, but ONE transaction, so the basket lands whole or not at all.
     */
    suspend fun logProducts(
        items: List<Pair<FoodProductEntity, Double>>,
        meal: MealSlot,
        epochDay: Long,
    ) {
        val now = System.currentTimeMillis()
        foodLogDao.insertAll(
            items.map { (product, grams) ->
                entryFor(product, grams, servings = null, meal, epochDay, now)
            }
        )
        partnerScheduling.onMealLogged(meal, epochDay)
    }

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
        partnerScheduling.onMealLogged(meal, epochDay)
    }
}
