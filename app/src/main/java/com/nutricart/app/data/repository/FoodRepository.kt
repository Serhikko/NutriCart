package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.OpenFoodFactsApi
import android.util.Log
import com.nutricart.app.data.remote.dto.toEntityOrNull
import com.nutricart.app.domain.logic.ProductRanker
import com.nutricart.app.domain.model.ProductSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
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
    private val shops: ZakazShops,
) {

    /**
     * Barcode lookup: Found, Incomplete (OFF or a Ukrainian shop knows the
     * product only partly) or NotFound. The order of the checks — the user's
     * own product first, then the cache, then Open Food Facts form by form,
     * then the Ukrainian shops — lives in the pure [lookUpBarcode],
     * unit-tested on the JVM; this only wires it to Room, Retrofit and
     * [ZakazShops]. No internet with nothing to show still throws
     * IOException, so the user sees "you are offline" instead of "not found".
     */
    suspend fun byBarcode(scanned: String): BarcodeLookup =
        lookUpBarcode(
            scanned,
            lookupSources,
            nowMillis = System::currentTimeMillis,
            pause = { delay(it) },
        )

    private val lookupSources = object : BarcodeLookupSources {
        override suspend fun ownProduct(code: String): FoodProductEntity? =
            foodDao.byId(FoodProductEntity.localBarcodeId(code))

        override suspend fun cachedOffProduct(code: String): FoodProductEntity? =
            foodDao.byId("off:$code")

        override suspend fun cachedShopProduct(code: String): FoodProductEntity? =
            foodDao.byId(FoodProductEntity.zakazId(code))

        override suspend fun save(product: FoodProductEntity) = foodDao.upsert(product)

        override suspend fun askShops(code14: String): ShopAnswer = shops.ask(code14)

        override suspend fun askOff(code: String): OffAnswer = try {
            val response = api.productByBarcode(code)
            val product = response.product
            if (product == null || response.status == 0) OffAnswer.Unknown
            else OffAnswer.Product(product)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            when (e.code()) {
                // The v2 endpoint answers 404 for an unknown code: keep going.
                404 -> OffAnswer.Unknown
                // Rate-limited (OFF allows 15 product reads a minute per IP)
                // or overloaded: "busy", never "not found".
                429, 503 -> OffAnswer.Busy(
                    retryAfterSeconds(e.response()?.headers()?.get("Retry-After")),
                )
                // Anything else is a server problem — use what we have.
                else -> {
                    Log.w("FoodRepository", "Barcode lookup failed with HTTP ${e.code()}")
                    OffAnswer.Failed
                }
            }
        } catch (e: SocketTimeoutException) {
            // Connected, but OFF didn't answer in time: OFF is unavailable,
            // the phone is not offline (and the shops may still answer).
            Log.w("FoodRepository", "Barcode lookup timed out")
            OffAnswer.Failed
        } catch (e: IOException) {
            OffAnswer.Offline(e)
        } catch (e: Exception) {
            // Unexpected response shape — log it, use what we have.
            Log.w("FoodRepository", "Barcode lookup failed", e)
            OffAnswer.Failed
        }
    }

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
            // User-created and shop products first (the API cannot know them),
            // then the API results — and the whole list re-ranked by how often
            // the user actually logs each product. (The offline branch needs no
            // merge — searchByName already covers all sources.)
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

    /**
     * Creates a user-defined product and returns it ready for logging.
     *
     * With a [barcode] (a scan that Open Food Facts didn't know, or knew only
     * partly) the product is saved as "local:barcode:<digits>", which the
     * scanner checks first — so the next scan of the same pack finds it.
     * Saving under that id again replaces the values but keeps the star.
     */
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
        barcode: String? = null,
        isLiquid: Boolean = false,
    ): FoodProductEntity {
        val id = barcode?.takeIf { it.isNotBlank() }?.let { FoodProductEntity.localBarcodeId(it) }
            ?: ("local:" + UUID.randomUUID())
        val existing = if (barcode.isNullOrBlank()) null else foodDao.byId(id)
        val product = FoodProductEntity(
            id = id,
            name = name.trim(),
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            kcalPer100g = kcalPer100g,
            proteinPer100g = proteinPer100g,
            fatPer100g = fatPer100g,
            carbsPer100g = carbsPer100g,
            servingSizeG = servingSizeG,
            isLiquid = isLiquid,
            fiberPer100g = fiberPer100g,
            sugarsPer100g = sugarsPer100g,
            saltPer100g = saltPer100g,
            saturatedFatPer100g = saturatedFatPer100g,
            source = ProductSource.LOCAL,
            cachedAtEpochMillis = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
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
