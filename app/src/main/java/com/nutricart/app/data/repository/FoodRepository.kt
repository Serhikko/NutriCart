package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.OpenFoodFactsApi
import android.util.Log
import com.nutricart.app.data.remote.dto.toEntityOrNull
import kotlinx.coroutines.CancellationException
import java.io.IOException
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
     * Barcode lookup, offline-first: the cache answers instantly for products
     * scanned before; otherwise the OFF barcode endpoint is asked and the
     * result is cached like any other product.
     * null = the database genuinely does not know this barcode.
     * No internet is NOT "unknown" — an IOException propagates to the caller,
     * which tells the user they are offline instead of lying "not found".
     */
    suspend fun byBarcode(barcode: String): FoodProductEntity? {
        foodDao.byId("off:$barcode")?.let { return it }
        return try {
            val product = api.productByBarcode(barcode).product
                ?.toEntityOrNull(System.currentTimeMillis())
                ?: return null
            foodDao.upsertAll(listOf(product))
            product
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e // offline — the ViewModel shows a dedicated message
        } catch (e: Exception) {
            // Server error or unexpected response — log it, treat as unknown.
            Log.w("FoodRepository", "Barcode lookup failed", e)
            null
        }
    }

    suspend fun search(query: String): FoodSearchResult {
        return try {
            val dtos = api.searchByName(query).products
            val now = System.currentTimeMillis()
            // Incomplete products (no name / missing macros) are dropped here.
            val products = dtos.mapNotNull { it.toEntityOrNull(now) }
            // Spec rule: EVERY looked-up product goes into the cache,
            // so the app keeps working without internet.
            foodDao.upsertAll(products)
            FoodSearchResult(products, offline = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The genuine "no internet" signal from OkHttp — use the cache.
            FoodSearchResult(foodDao.searchByName(query), offline = true)
        } catch (e: Exception) {
            // Anything else (server error, unexpected response shape): also fall
            // back to the cache, but leave a log line so breakage is diagnosable.
            Log.w("FoodRepository", "Food search failed, using cache", e)
            FoodSearchResult(foodDao.searchByName(query), offline = true)
        }
    }
}
