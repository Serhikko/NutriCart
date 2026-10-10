package com.nutricart.app.data.repository

import android.util.Log
import com.nutricart.app.data.remote.ZakazApi
import com.nutricart.app.data.remote.dto.ZAKAZ_FALLBACK_STORES
import com.nutricart.app.data.remote.dto.pickZakazStores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The Ukrainian shops' tier of the barcode lookup: asks a handful of stores
 * of the zakaz.ua API (one per big chain) whether they sell a product, all
 * at once, and reports what they said as a [ShopAnswer]. What to make of the
 * cards is decided by the pure lookUpBarcode.
 *
 * Politeness, since the API is not ours: one point lookup per scan, and only
 * after Open Food Facts had nothing usable; the store list at most once a
 * day; never more than six stores. The whole tier gives up after about
 * eight seconds, so a slow shop never keeps the user waiting long.
 */
@Singleton
class ZakazShops @Inject constructor(
    private val api: ZakazApi,
) {

    // The store list, per process: fetched on the first shop lookup.
    private val storesLock = Mutex()
    private var stores: List<String>? = null
    private var storesFetched: TimeMark? = null
    private var storesFailed: TimeMark? = null

    /** Every chosen store asked for [code14] in parallel; see [shopAnswerOf]. */
    suspend fun ask(code14: String): ShopAnswer {
        val started = TimeSource.Monotonic.markNow()
        val storeIds = storeIds()
        val left = TIER_BUDGET - started.elapsedNow()
        val replies = coroutineScope {
            storeIds.map { storeId ->
                // A store still silent when the budget runs out didn't answer.
                async { withTimeoutOrNull(left) { askStore(storeId, code14) } ?: StoreReply.NoAnswer }
            }.awaitAll()
        }
        return shopAnswerOf(replies)
    }

    private suspend fun askStore(storeId: String, code14: String): StoreReply = try {
        StoreReply.Listed(api.product(storeId, code14))
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 404) {
            StoreReply.NotListed
        } else {
            Log.w(TAG, "Store $storeId answered HTTP ${e.code()}")
            StoreReply.NoAnswer
        }
    } catch (e: Exception) {
        // No connection, a timeout, a body that isn't JSON: no answer.
        StoreReply.NoAnswer
    }

    /**
     * The stores to ask: the list picked from the API's store list, fetched
     * at most once a day. A failed fetch falls back to a fixed list for this
     * lookup and is retried on a later one, at most every ten minutes, so a
     * short outage doesn't pin the fallback for a whole day.
     */
    private suspend fun storeIds(): List<String> {
        storesLock.withLock {
            val known = stores
            if (known != null && storesFetched?.let { it.elapsedNow() < STORES_TTL } == true) return known
            if (storesFailed?.let { it.elapsedNow() < STORES_RETRY } == true) return ZAKAZ_FALLBACK_STORES

            val picked = try {
                withTimeoutOrNull(STORES_TIMEOUT) { pickZakazStores(api.stores()) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Store list failed: ${e.javaClass.simpleName}")
                emptyList()
            }
            if (picked.isEmpty()) {
                storesFailed = TimeSource.Monotonic.markNow()
                return ZAKAZ_FALLBACK_STORES
            }
            stores = picked
            storesFetched = TimeSource.Monotonic.markNow()
            storesFailed = null
            return picked
        }
    }

    private companion object {
        const val TAG = "ZakazShops"

        /** The whole tier, store list included. */
        val TIER_BUDGET = 8.seconds

        /** The store list gets part of the budget; the stores need the rest. */
        val STORES_TIMEOUT = 3.seconds
        val STORES_TTL = 24.hours
        val STORES_RETRY = 10.minutes
    }
}
