package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.SavedMealDao
import com.nutricart.app.data.local.dao.SavedMealSummary
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.SavedMealEntity
import com.nutricart.app.data.local.entity.SavedMealItemEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Saved meals: named food combos created from diary sections. */
@Singleton
class SavedMealRepository @Inject constructor(
    private val savedMealDao: SavedMealDao,
) {

    suspend fun summaries(): List<SavedMealSummary> = savedMealDao.summaries()

    suspend fun itemsFor(mealId: Long): List<SavedMealItemEntity> =
        savedMealDao.itemsFor(mealId)

    /** Freezes the given diary entries under a name (id 0 = auto-generate). */
    suspend fun saveFromEntries(name: String, entries: List<FoodLogEntryEntity>) {
        savedMealDao.insertMealWithItems(
            SavedMealEntity(
                name = name,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
            entries.map { e ->
                SavedMealItemEntity(
                    mealId = 0, // replaced with the real id inside the transaction
                    productId = e.productId,
                    name = e.name,
                    grams = e.grams,
                    servings = e.servings,
                    kcal = e.kcal,
                    proteinG = e.proteinG,
                    fatG = e.fatG,
                    carbsG = e.carbsG,
                    fiberG = e.fiberG,
                    sugarsG = e.sugarsG,
                    saltG = e.saltG,
                    saturatedFatG = e.saturatedFatG,
                )
            },
        )
    }

    suspend fun delete(mealId: Long) = savedMealDao.deleteMeal(mealId)
}
