package com.nutricart.app.data.repository

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.dto.ProductDto
import com.nutricart.app.data.remote.dto.ZakazMatch
import com.nutricart.app.data.remote.dto.mapZakazCard
import com.nutricart.app.data.remote.dto.toEntity
import com.nutricart.app.data.remote.dto.toEntityOrNull
import com.nutricart.app.data.remote.dto.toPrefill
import com.nutricart.app.domain.logic.BarcodeNormalizer
import com.nutricart.app.domain.logic.BarcodeOrigin
import com.nutricart.app.domain.logic.ShopBarcodes
import com.nutricart.app.domain.logic.lookupNotice
import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ShopsStatus
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/** The three outcomes of a barcode scan. */
sealed interface BarcodeLookup {

    /** A usable product: straight to the amount dialog. */
    data class Found(val product: FoodProductEntity) : BarcodeLookup

    /**
     * Open Food Facts or a Ukrainian shop has the product, but not enough to
     * log it (no name, a core value missing, or shop values that don't add
     * up): the user completes it from the label. [fromShop] picks the
     * message, so the form never credits Open Food Facts with shop data.
     */
    data class Incomplete(val prefill: ProductPrefill, val fromShop: Boolean = false) : BarcodeLookup

    /**
     * Nobody knows this code. [barcode] is the scanned digits (the first
     * normalizer candidate), the code a new product gets saved under;
     * [country], [offUnavailable] and [shops] pick the message ([notice]):
     * a source that did not answer is never said not to have the product.
     */
    data class NotFound(
        val barcode: String,
        val country: BarcodeCountry?,
        /** Open Food Facts was busy, failed or didn't answer: trying again may help. */
        val offUnavailable: Boolean = false,
        val shops: ShopsStatus = ShopsStatus.NOT_ASKED,
    ) : BarcodeLookup {
        val notice: LookupNotice get() = lookupNotice(country, offUnavailable, shops)
    }
}

/** What Open Food Facts answered for ONE candidate code. */
sealed interface OffAnswer {
    /** The product as OFF has it — usable or not. */
    data class Product(val dto: ProductDto) : OffAnswer

    /** 404 / status 0 / no product: not under this form, try the next one. */
    data object Unknown : OffAnswer

    /**
     * HTTP 429 or 503: OFF is rate-limiting us or overloaded. Not "unknown":
     * the product may well be there. [retryAfterSeconds] is the Retry-After
     * header when it is a whole number of seconds.
     */
    data class Busy(val retryAfterSeconds: Long?) : OffAnswer

    /** No connection at all. */
    data class Offline(val cause: IOException) : OffAnswer

    /**
     * Any other failure (a timeout, a server error, an unexpected response):
     * stop asking, OFF is unavailable for this lookup.
     */
    data object Failed : OffAnswer
}

/** What the Ukrainian shops' catalogue answered for one scan; see [shopAnswerOf]. */
sealed interface ShopAnswer {
    /** The product cards of every store that has the code, best store first. */
    data class Hits(val cards: List<JsonElement>) : ShopAnswer

    /** No store has it, and at least one store said so (404). */
    data object Miss : ShopAnswer

    /** No store answered at all. */
    data object Unavailable : ShopAnswer
}

/** How one store answered a product request. */
sealed interface StoreReply {
    /** 200: the store sells it; [card] is the body as sent. */
    data class Listed(val card: JsonElement) : StoreReply

    /** 404: the store answered, and doesn't list this code. */
    data object NotListed : StoreReply

    /** Anything else: a timeout, no connection, a server error, a body that isn't JSON. */
    data object NoAnswer : StoreReply
}

/**
 * The stores' replies, in store-preference order, as one answer: the cards
 * of every store that has the code (in that order), else Miss when at least
 * one store answered, else Unavailable.
 */
fun shopAnswerOf(replies: List<StoreReply>): ShopAnswer {
    val cards = replies.filterIsInstance<StoreReply.Listed>().map { it.card }
    return when {
        cards.isNotEmpty() -> ShopAnswer.Hits(cards)
        replies.any { it is StoreReply.NotListed } -> ShopAnswer.Miss
        else -> ShopAnswer.Unavailable
    }
}

/**
 * The Retry-After header as whole seconds; null when absent or not plain
 * digits (an HTTP date, a sign), and the default wait applies. Digits too
 * many for a Long are still a number of seconds — a very long wait, so no
 * retry — as on the website.
 */
fun retryAfterSeconds(header: String?): Long? {
    val value = header?.trim()?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } } ?: return null
    return value.toLongOrNull() ?: Long.MAX_VALUE
}

/** Where the lookup reads and writes; FoodRepository wires it to Room and Retrofit. */
interface BarcodeLookupSources {
    /** The user's own product saved under [code] ("local:barcode:<code>"). */
    suspend fun ownProduct(code: String): FoodProductEntity?

    /** The cached Open Food Facts row for [code] ("off:<code>"). */
    suspend fun cachedOffProduct(code: String): FoodProductEntity?

    /** The cached Ukrainian-shop row for [code] ("zakaz:<code>"). */
    suspend fun cachedShopProduct(code: String): FoodProductEntity?

    suspend fun askOff(code: String): OffAnswer

    /** Asks the Ukrainian shops for [code14], the GTIN-14 (ShopBarcodes.zakazCode). */
    suspend fun askShops(code14: String): ShopAnswer

    /** Caches a fresh product (from Open Food Facts or a shop). */
    suspend fun save(product: FoodProductEntity)
}

/**
 * Barcode lookup, offline-first. Pure orchestration (no Android, no
 * Retrofit, no coroutines library) so the order of the checks is unit-tested
 * on the JVM; the website (web/src/lib/openFoodFacts.ts) follows the same
 * order.
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
 *  3. A cached shop row answers instantly too, never refreshed: the shops
 *     never state detail nutrients, so there is nothing to catch up on.
 *  4. Open Food Facts, once per form that differs in more than leading
 *     zeros (OFF itself ignores them, and every extra request counts
 *     against its 15-a-minute limit). A usable product is cached (keeping
 *     the star) and returned. A product that is there but incomplete is
 *     remembered — the FIRST one with a name or a core value; a bare stub
 *     record counts as nothing — and the next form is still tried, since
 *     another form may be complete. 404 means "not under this form". Busy
 *     (429/503) is asked once more after a short [pause], once per lookup,
 *     unless OFF asks for more than five seconds. Busy again, or any other
 *     failure, ends this step: OFF is unavailable, and the message will say
 *     so rather than "not found".
 *  5. The stale cached OFF row, if any.
 *  6. The Ukrainian shops' catalogue, for codes they may sell (not
 *     Belarusian or UK ones): a usable product is cached as
 *     "zakaz:<scanned digits>" and returned; a partial one is remembered.
 *  7. The remembered partial product: OFF's first, else the shop's
 *     (Incomplete).
 *  8. No internet with nothing to show is NOT "unknown": when OFF could not
 *     connect and no shop answered either, the IOException propagates and
 *     the user sees "you are offline" instead of "not found".
 *  9. NotFound, saying which sources were asked and which didn't answer.
 *
 * [pause] waits the given milliseconds (FoodRepository passes delay), so
 * tests run without sleeping.
 */
suspend fun lookUpBarcode(
    scanned: String,
    sources: BarcodeLookupSources,
    nowMillis: () -> Long,
    pause: suspend (Long) -> Unit,
): BarcodeLookup {
    val candidates = BarcodeNormalizer.candidates(scanned)
    // No digits at all — nothing to look up, nothing to save under.
    val scannedCode = candidates.firstOrNull() ?: return BarcodeLookup.NotFound("", null)

    candidates.firstNotNullOfOrNull { sources.ownProduct(it) }
        ?.let { return BarcodeLookup.Found(it) }

    val cached = candidates.firstNotNullOfOrNull { sources.cachedOffProduct(it) }
    if (cached != null && !cached.missingDetails()) return BarcodeLookup.Found(cached)

    candidates.firstNotNullOfOrNull { sources.cachedShopProduct(it) }
        ?.let { return BarcodeLookup.Found(it) }

    var offPartial: ProductPrefill? = null
    // Why OFF stopped early (Busy, Offline or Failed); null = it answered every form.
    var offTrouble: OffAnswer? = null
    var retryLeft = true
    for (code in candidates.distinctBy { it.trimStart('0') }) {
        var answer = sources.askOff(code)
        if (answer is OffAnswer.Busy && retryLeft) {
            retryLeft = false
            val wait = answer.retryAfterSeconds
            // A long Retry-After means "not now": the user is not kept waiting.
            if (wait == null || wait <= MAX_RETRY_WAIT_SECONDS) {
                pause(maxOf(1L, wait ?: DEFAULT_RETRY_WAIT_SECONDS) * 1000)
                answer = sources.askOff(code)
            }
        }
        when (answer) {
            is OffAnswer.Product -> {
                val product = answer.dto.toEntityOrNull(nowMillis())
                if (product != null) {
                    // Same star rule as search: fresh API data must not wipe it.
                    val merged = product.copy(isFavorite = cached?.isFavorite ?: false)
                    sources.save(merged)
                    return BarcodeLookup.Found(merged)
                }
                // A bare stub (no name, no core value) is nothing to prefill:
                // it must not hide a later form's or a shop's real prefill.
                if (offPartial == null) offPartial = answer.dto.toPrefill(code)?.takeIf { it.knowsAnything }
            }
            OffAnswer.Unknown -> Unit
            is OffAnswer.Busy, is OffAnswer.Offline, OffAnswer.Failed -> {
                offTrouble = answer
                break
            }
        }
    }

    cached?.let { return BarcodeLookup.Found(it) }

    var shops = ShopsStatus.NOT_ASKED
    var shopPartial: ProductPrefill? = null
    val code14 = ShopBarcodes.zakazCode(scannedCode)
    if (code14 != null && ShopBarcodes.shouldAskShops(scannedCode)) {
        when (val answer = sources.askShops(code14)) {
            is ShopAnswer.Hits -> {
                val matches = answer.cards.mapNotNull { mapZakazCard(it, scannedCode) }
                val usable = matches.filterIsInstance<ZakazMatch.Usable>().firstOrNull()
                if (usable != null) {
                    // No row existed a moment ago (step 3), but keep a star a
                    // concurrent save may have brought, like every refresh.
                    val star = sources.cachedShopProduct(scannedCode)?.isFavorite ?: false
                    val product = usable.toEntity(nowMillis(), isFavorite = star)
                    sources.save(product)
                    return BarcodeLookup.Found(product)
                }
                shopPartial = matches.firstOrNull()?.prefill
                // A card with nothing usable in it counts as not listed.
                if (shopPartial == null) shops = ShopsStatus.MISS
            }
            ShopAnswer.Miss -> shops = ShopsStatus.MISS
            ShopAnswer.Unavailable -> shops = ShopsStatus.UNAVAILABLE
        }
    }

    offPartial?.let { return BarcodeLookup.Incomplete(it) }
    shopPartial?.let { return BarcodeLookup.Incomplete(it, fromShop = true) }

    // Offline: the ViewModel shows a dedicated message — unless a shop did
    // answer, which proves a connection: then the message says which source
    // answered (no shop lists it) and which didn't (Open Food Facts).
    val trouble = offTrouble
    if (trouble is OffAnswer.Offline && shops != ShopsStatus.MISS) throw trouble.cause

    return BarcodeLookup.NotFound(
        barcode = scannedCode,
        country = BarcodeOrigin.countryOf(scannedCode),
        offUnavailable = trouble != null,
        shops = shops,
    )
}

/** Retry-After above this many seconds: don't retry, report OFF as busy. */
private const val MAX_RETRY_WAIT_SECONDS = 5L

/** The wait before the one retry when OFF gives no Retry-After. */
private const val DEFAULT_RETRY_WAIT_SECONDS = 2L

/**
 * True for OFF rows cached before v0.11 — no detail nutrient is known.
 * Only ever asked of "off:" rows: shop rows never have detail nutrients,
 * and no refresh could add them.
 */
internal fun FoodProductEntity.missingDetails(): Boolean =
    fiberPer100g == null && sugarsPer100g == null &&
        saltPer100g == null && saturatedFatPer100g == null
