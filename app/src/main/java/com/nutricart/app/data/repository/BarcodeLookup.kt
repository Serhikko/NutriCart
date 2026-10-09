package com.nutricart.app.data.repository

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.dto.ProductDto
import com.nutricart.app.data.remote.dto.toEntityOrNull
import com.nutricart.app.data.remote.dto.toPrefill
import com.nutricart.app.domain.logic.BarcodeNormalizer
import com.nutricart.app.domain.logic.BarcodeOrigin
import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.ProductPrefill
import java.io.IOException

/** The three outcomes of a barcode scan. */
sealed interface BarcodeLookup {

    /** A usable product: straight to the amount dialog. */
    data class Found(val product: FoodProductEntity) : BarcodeLookup

    /**
     * Open Food Facts has the product, but not enough to log it (no name, or
     * a core value missing): the user completes it from the label.
     */
    data class Incomplete(val prefill: ProductPrefill) : BarcodeLookup

    /**
     * Nobody knows this code. [barcode] is the scanned digits (the first
     * normalizer candidate), the code a new product gets saved under;
     * [country] picks the message ("this Ukrainian product...").
     */
    data class NotFound(val barcode: String, val country: BarcodeCountry?) : BarcodeLookup
}

/** What Open Food Facts answered for ONE candidate code. */
sealed interface OffAnswer {
    /** The product as OFF has it — usable or not. */
    data class Product(val dto: ProductDto) : OffAnswer

    /** 404 / status 0 / no product: not under this form, try the next one. */
    data object Unknown : OffAnswer

    /** No connection at all. */
    data class Offline(val cause: IOException) : OffAnswer

    /** Any other failure (server error, unexpected response): stop asking. */
    data object Failed : OffAnswer
}

/** Where the lookup reads and writes; FoodRepository wires it to Room and Retrofit. */
interface BarcodeLookupSources {
    /** The user's own product saved under [code] ("local:barcode:<code>"). */
    suspend fun ownProduct(code: String): FoodProductEntity?

    /** The cached Open Food Facts row for [code] ("off:<code>"). */
    suspend fun cachedOffProduct(code: String): FoodProductEntity?

    suspend fun askOff(code: String): OffAnswer

    /** Caches a fresh Open Food Facts product. */
    suspend fun save(product: FoodProductEntity)
}

/**
 * Barcode lookup, offline-first. Pure orchestration (no Android, no
 * Retrofit) so the order of the checks is unit-tested on the JVM; the
 * website (web/src/lib/openFoodFacts.ts) follows the same order.
 *
 * The scanned digits are first turned into every form the database might
 * store them in (BarcodeNormalizer: a UPC-A gets its leading zero, a UPC-E
 * is expanded, and so on). Then:
 *
 *  1. The user's own product saved under ANY of those forms wins — it is
 *     what they typed from the label, and OFF may still have the gap.
 *  2. A fully-detailed cached OFF row answers instantly. A row cached BEFORE
 *     the detail-nutrient columns existed (all four null) gets an online
 *     refresh attempt — OFF may well know the values, and without this the
 *     user's most-scanned staples would show "—" forever (review-caught).
 *  3. Open Food Facts, form by form. A usable product is cached (keeping the
 *     star) and returned. A product that is there but incomplete is
 *     remembered — the FIRST one — and the next form is still tried, since
 *     another form may be complete. 404 means "not under this form".
 *  4. Then: the stale cached row, else the remembered partial product
 *     (Incomplete), else NotFound.
 *
 * Errors end step 3 early and fall back like step 4. No internet with
 * nothing to fall back on is NOT "unknown": the IOException propagates and
 * the user sees "you are offline" instead of "not found".
 */
suspend fun lookUpBarcode(
    scanned: String,
    sources: BarcodeLookupSources,
    nowMillis: () -> Long,
): BarcodeLookup {
    val candidates = BarcodeNormalizer.candidates(scanned)
    // No digits at all — nothing to look up, nothing to save under.
    val scannedCode = candidates.firstOrNull() ?: return BarcodeLookup.NotFound("", null)
    val notFound = BarcodeLookup.NotFound(scannedCode, BarcodeOrigin.countryOf(scannedCode))

    candidates.firstNotNullOfOrNull { sources.ownProduct(it) }
        ?.let { return BarcodeLookup.Found(it) }

    val cached = candidates.firstNotNullOfOrNull { sources.cachedOffProduct(it) }
    if (cached != null && !cached.missingDetails()) return BarcodeLookup.Found(cached)

    var partial: ProductPrefill? = null
    fun fallback(): BarcodeLookup? =
        cached?.let { BarcodeLookup.Found(it) } ?: partial?.let { BarcodeLookup.Incomplete(it) }

    for (code in candidates) {
        when (val answer = sources.askOff(code)) {
            is OffAnswer.Product -> {
                val product = answer.dto.toEntityOrNull(nowMillis())
                if (product != null) {
                    // Same star rule as search: fresh API data must not wipe it.
                    val merged = product.copy(isFavorite = cached?.isFavorite ?: false)
                    sources.save(merged)
                    return BarcodeLookup.Found(merged)
                }
                if (partial == null) partial = answer.dto.toPrefill(code)
            }
            OffAnswer.Unknown -> Unit
            // Offline: the ViewModel shows a dedicated message.
            is OffAnswer.Offline -> return fallback() ?: throw answer.cause
            OffAnswer.Failed -> return fallback() ?: notFound
        }
    }
    return fallback() ?: notFound // every form answered "unknown"
}

/** True for rows cached before v0.11 — no detail nutrient is known. */
internal fun FoodProductEntity.missingDetails(): Boolean =
    fiberPer100g == null && sugarsPer100g == null &&
        saltPer100g == null && saturatedFatPer100g == null
