package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.OpenFoodFactsApi
import android.util.Log
import com.nutricart.app.data.remote.dto.toEntityOrNull
import com.nutricart.app.domain.logic.ProductRanker
import com.nutricart.app.domain.model.ProductSource
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Search results plus a flag telling the UI they came from the offline cache. */
data class FoodSearchResult(
    val products: List<FoodProductEntity>,
    val offline: Boolean,
)

/**
 * Offline-first food search: try the network, cache every result, and fall
 * back to the local cache when the network is unavailable.
 */
@Singleton
class FoodRepository @Inject constructor(
    private val api: OpenFoodFactsApi,
    private val foodDao: FoodDao,
) {

    /**
     * Barcode lookup, offline-first: a fully-detailed cache row answers
     * instantly. A row cached BEFORE the detail-nutrient columns existed
     * (all four null) gets an online refresh attempt — OFF may well know the
     * values, and without this the user's most-scanned staples would show
     * "—" forever (review-caught). Offline, the stale row still wins over an
     * error.
     * null = the database genuinely does not know this barcode.
     * No internet with NO cached row is NOT "unknown" — the IOException
     * propagates, and the user sees "you are offline" instead of "not found".
     */
    suspend fun byBarcode(barcode: String): FoodProductEntity? {
        val cached = foodDao.byId("off:$barcode")
        if (cached != null && !cached.missingDetails()) return cached
        return try {
            val product = api.productByBarcode(barcode).product
                ?.toEntityOrNull(System.currentTimeMillis())
                ?: return cached // OFF lost/hides the product — keep what we have
            // Same star rule as search: fresh API data must not wipe it.
            val merged = product.copy(isFavorite = cached?.isFavorite ?: false)
            foodDao.upsert(merged)
            merged
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            cached ?: throw e // offline — the ViewModel shows a dedicated message
        } catch (e: Exception) {
            // Server error or unexpected response — log it, use what we have.
            Log.w("FoodRepository", "Barcode lookup failed", e)
            cached
        }
    }

    /** True for rows cached before v0.11 — no detail nutrient is known. */
    private fun FoodProductEntity.missingDetails(): Boolean =
        fiberPer100g == null && sugarsPer100g == null &&
            saltPer100g == null && saturatedFatPer100g == null

    suspend fun search(query: String): FoodSearchResult {
        return try {
            val dtos = api.searchByName(query).products
            val now = System.currentTimeMillis()
            // Fresh API rows don't know about stars — carry them over, or the
            // upsert below would silently un-favorite every re-searched product.
            val favIds = foodDao.favoriteIds().toSet()
            // Incomplete products (no name / missing macros) are dropped here.
            val products = dtos
                .mapNotNull { it.toEntityOrNull(now) }
                .map { it.copy(isFavorite = it.id in favIds) }
            // Spec rule: EVERY looked-up product goes into the cache,
            // so the app keeps working without internet.
            foodDao.upsertAll(products)
            // User-created products first (the API cannot know them), then the
            // API results — and the whole list re-ranked by how often the user
            // actually logs each product. (The offline branch needs no merge —
            // searchByName already covers all sources.)
            FoodSearchResult(
                ranked(foodDao.searchLocalByName(query) + products),
                offline = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The genuine "no internet" signal from OkHttp — use the cache.
            FoodSearchResult(ranked(foodDao.searchByName(query)), offline = true)
        } catch (e: Exception) {
            // Anything else (server error, unexpected response shape): also fall
            // back to the cache, but leave a log line so breakage is diagnosable.
            Log.w("FoodRepository", "Food search failed, using cache", e)
            FoodSearchResult(ranked(foodDao.searchByName(query)), offline = true)
        }
    }

    /** Frequently-logged first; ties keep the incoming (relevance) order. */
    private suspend fun ranked(products: List<FoodProductEntity>): List<FoodProductEntity> {
        val counts = foodDao.productUseCounts().associate { it.productId to it.uses }
        return ProductRanker.rank(products, idOf = { it.id }, useCounts = counts)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = foodDao.setFavorite(id, favorite)

    suspend fun searchFavorites(query: String): List<FoodProductEntity> =
        foodDao.searchFavorites(query)

    /** Top products for the "frequent" list under an empty search box. */
    suspend fun frequentProducts(): List<FoodProductEntity> = foodDao.frequentProducts(10)

    /** Creates a user-defined product and returns it ready for logging. */
    suspend fun createCustomProduct(
        name: String,
        brand: String?,
        kcalPer100g: Double,
        proteinPer100g: Double,
        fatPer100g: Double,
        carbsPer100g: Double,
        servingSizeG: Double?,
        fiberPer100g: Double?,
        sugarsPer100g: Double?,
        saltPer100g: Double?,
        saturatedFatPer100g: Double?,
    ): FoodProductEntity {
        val product = FoodProductEntity(
            id = "local:" + UUID.randomUUID(),
            name = name.trim(),
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            kcalPer100g = kcalPer100g,
            proteinPer100g = proteinPer100g,
            fatPer100g = fatPer100g,
            carbsPer100g = carbsPer100g,
            servingSizeG = servingSizeG,
            fiberPer100g = fiberPer100g,
            sugarsPer100g = sugarsPer100g,
            saltPer100g = saltPer100g,
            saturatedFatPer100g = saturatedFatPer100g,
            source = ProductSource.LOCAL,
            cachedAtEpochMillis = System.currentTimeMillis(),
        )
        foodDao.upsert(product)
        return product
    }

    /** Diary entries are unaffected: they snapshot nutrition at log time. */
    suspend fun updateCustomProduct(product: FoodProductEntity) {
        require(product.source == ProductSource.LOCAL) {
            "Only user-created products are editable"
        }
        foodDao.upsert(product)
    }

    suspend fun deleteCustomProduct(id: String) = foodDao.deleteLocal(id)
}
