package com.nutricart.app.data.repository

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.dto.NutrimentsDto
import com.nutricart.app.data.remote.dto.ProductDto
import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.ProductSource
import com.nutricart.app.domain.model.ShopsStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * The order of the barcode checks (the website's lookup follows the same
 * order): the user's own product, a fresh cache row, a cached shop row, Open
 * Food Facts form by form, the stale cache row, the Ukrainian shops, then
 * the partial products and the messages.
 */
class BarcodeLookupTest {

    /** Runs a suspend block whose fakes never really suspend — no coroutines library needed. */
    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return checkNotNull(outcome) { "the block suspended" }.getOrThrow()
    }

    private class FakeSources(
        val own: Map<String, FoodProductEntity> = emptyMap(),
        val cache: Map<String, FoodProductEntity> = emptyMap(),
        val shopCache: MutableMap<String, FoodProductEntity> = mutableMapOf(),
        /** Codes missing here answer Unknown (a 404). */
        val off: Map<String, OffAnswer> = emptyMap(),
        /** The answer when a code is asked again (the busy retry); else [off] again. */
        val offAgain: Map<String, OffAnswer> = emptyMap(),
        /** Most codes are worth asking the shops about: default to "no shop has it". */
        val shops: ShopAnswer = ShopAnswer.Miss,
        /** Runs while the shops are being asked. */
        val duringShops: FakeSources.() -> Unit = {},
    ) : BarcodeLookupSources {
        val asked = mutableListOf<String>()
        val shopsAsked = mutableListOf<String>()
        val saved = mutableListOf<FoodProductEntity>()

        override suspend fun ownProduct(code: String): FoodProductEntity? = own[code]
        override suspend fun cachedOffProduct(code: String): FoodProductEntity? = cache[code]
        override suspend fun cachedShopProduct(code: String): FoodProductEntity? = shopCache[code]
        override suspend fun askOff(code: String): OffAnswer {
            val again = code in asked
            asked += code
            return (if (again) offAgain[code] else null) ?: off[code] ?: OffAnswer.Unknown
        }
        override suspend fun askShops(code14: String): ShopAnswer {
            shopsAsked += code14
            duringShops()
            return shops
        }
        override suspend fun save(product: FoodProductEntity) {
            saved += product
        }
    }

    /** Every pause the lookup asked for, in milliseconds; nobody really waits. */
    private val pauses = mutableListOf<Long>()

    private fun lookUp(scanned: String, sources: FakeSources): BarcodeLookup =
        runSuspend { lookUpBarcode(scanned, sources, nowMillis = { 42L }, pause = { pauses += it }) }

    private fun entity(
        id: String,
        name: String = "Product",
        source: ProductSource = ProductSource.OPEN_FOOD_FACTS,
        fiber: Double? = 1.0,
        isFavorite: Boolean = false,
    ) = FoodProductEntity(
        id = id, name = name, brand = null,
        kcalPer100g = 100.0, proteinPer100g = 1.0, fatPer100g = 1.0, carbsPer100g = 1.0,
        servingSizeG = null, fiberPer100g = fiber,
        source = source, cachedAtEpochMillis = 0L, isFavorite = isFavorite,
    )

    private fun complete(code: String, name: String = "Кефір") = OffAnswer.Product(
        ProductDto(
            code = code,
            productName = name,
            nutriments = NutrimentsDto(
                kcalPer100g = 53.0, proteinPer100g = 3.0, fatPer100g = 2.5, carbsPer100g = 4.0,
                fiberPer100g = 0.0,
            ),
        )
    )

    private fun incomplete(code: String, nameUk: String = "Хліб") = OffAnswer.Product(
        ProductDto(code = code, productNameUk = nameUk, nutriments = NutrimentsDto(kcalPer100g = 240.0))
    )

    private fun card(text: String): JsonElement = Json.parseToJsonElement(text)

    /** A usable shop card (spec vector V1). */
    private val shopCard = card(
        """{"title":"Нутелла паста горіхова 350г","producer":{"trademark":"Nutella"},"unit":"pcs",
           "nutrition_facts":{"ingredient_energy":"539.00ккал","ingredient_protein":"6,3г",
                              "ingredient_fat":"30,9г","ingredient_carbohydrates":"57,5г"}}"""
    )

    /** A shop card missing its carbohydrates: prefill only. */
    private val partialShopCard = card(
        """{"title":"Печиво","nutrition_facts":{"ingredient_energy":"452ккал","ingredient_protein":"7,1"}}"""
    )

    private val ua = "4820024700016"
    private val ua14 = "04820024700016"
    private val by = "4810268000013"
    private val uk = "5000112637922"

    // 12-digit UPC-A: candidates are the 12 digits, then the 13-digit padded form
    // — the same code to Open Food Facts, which ignores leading zeros.
    private val upc = "012345678905"
    private val upcPadded = "0012345678905"

    // UPC-E: the 8 digits, then the expanded UPC-A — two different codes to OFF.
    private val upcE = "01234565"
    private val upcEA = "012345000065"

    // --- Own products and the cache --------------------------------------

    @Test
    fun `the user's own product wins over Open Food Facts and is not even asked`() {
        val mine = entity(FoodProductEntity.localBarcodeId(ua), "Мій хліб", ProductSource.LOCAL)
        val sources = FakeSources(own = mapOf(ua to mine), off = mapOf(ua to complete(ua)))
        assertEquals(BarcodeLookup.Found(mine), lookUp(ua, sources))
        assertTrue(sources.asked.isEmpty())
        assertTrue(sources.shopsAsked.isEmpty())
    }

    @Test
    fun `the user's own product is found under any form of the code`() {
        val mine = entity(FoodProductEntity.localBarcodeId(upcPadded), source = ProductSource.LOCAL)
        val sources = FakeSources(own = mapOf(upcPadded to mine))
        assertEquals(BarcodeLookup.Found(mine), lookUp(upc, sources))
    }

    @Test
    fun `a fully detailed cache row answers without the network`() {
        val cached = entity("off:$ua")
        val sources = FakeSources(cache = mapOf(ua to cached), off = mapOf(ua to complete(ua)))
        assertEquals(BarcodeLookup.Found(cached), lookUp(ua, sources))
        assertTrue(sources.asked.isEmpty())
    }

    @Test
    fun `a stale cache row is refreshed and keeps its star`() {
        val stale = entity("off:$ua", fiber = null, isFavorite = true)
        val sources = FakeSources(cache = mapOf(ua to stale), off = mapOf(ua to complete(ua, "Fresh")))
        val product = (lookUp(ua, sources) as BarcodeLookup.Found).product
        assertEquals("Fresh", product.name)
        assertTrue(product.isFavorite)
    }

    @Test
    fun `a stale cache row beats an incomplete answer and an unknown code`() {
        val stale = entity("off:$ua", fiber = null)
        assertEquals(
            BarcodeLookup.Found(stale),
            lookUp(ua, FakeSources(cache = mapOf(ua to stale), off = mapOf(ua to incomplete(ua)))),
        )
        val sources = FakeSources(cache = mapOf(ua to stale))
        assertEquals(BarcodeLookup.Found(stale), lookUp(ua, sources))
        // Something to show already: the shops are not bothered.
        assertTrue(sources.shopsAsked.isEmpty())
    }

    @Test
    fun `a cached shop row answers without any network, never refreshed`() {
        val shopRow = entity(FoodProductEntity.zakazId(upcPadded), source = ProductSource.ZAKAZ, fiber = null)
        val sources = FakeSources(shopCache = mutableMapOf(upcPadded to shopRow), off = mapOf(upc to complete(upc)))
        assertEquals(BarcodeLookup.Found(shopRow), lookUp(upc, sources))
        assertTrue(sources.asked.isEmpty())
        assertTrue(sources.shopsAsked.isEmpty())
    }

    // --- Open Food Facts --------------------------------------------------

    @Test
    fun `OFF is asked once per code that differs in more than leading zeros`() {
        val sources = FakeSources(off = mapOf(upcPadded to complete(upcPadded)))
        lookUp(upc, sources)
        // OFF itself ignores the leading zeros: one request, not two.
        assertEquals(listOf(upc), sources.asked)
        // The own-product and cache checks still use every form.
        val cached = entity("off:$upcPadded")
        assertEquals(BarcodeLookup.Found(cached), lookUp(upc, FakeSources(cache = mapOf(upcPadded to cached))))
        // A UPC-E and its expansion are different codes: both are asked.
        val upcESources = FakeSources()
        lookUp(upcE, upcESources)
        assertEquals(listOf(upcE, upcEA), upcESources.asked)
    }

    @Test
    fun `an incomplete form does not stop the search - a complete one is Found and cached`() {
        val sources = FakeSources(off = mapOf(upcE to incomplete(upcE), upcEA to complete(upcEA)))
        val result = lookUp(upcE, sources)
        assertTrue(result is BarcodeLookup.Found)
        val product = (result as BarcodeLookup.Found).product
        assertEquals("off:$upcEA", product.id)
        assertEquals(42L, product.cachedAtEpochMillis)
        assertEquals(listOf(product), sources.saved)
        assertEquals(listOf(upcE, upcEA), sources.asked)
        assertTrue(sources.shopsAsked.isEmpty()) // OFF had it
    }

    @Test
    fun `only incomplete forms - the first one prefills the form`() {
        val sources = FakeSources(
            off = mapOf(upcE to incomplete(upcE, "Перший"), upcEA to incomplete(upcEA, "Другий")),
        )
        val result = lookUp(upcE, sources)
        assertTrue(result is BarcodeLookup.Incomplete)
        val incomplete = result as BarcodeLookup.Incomplete
        assertEquals(upcE, incomplete.prefill.barcode)
        assertEquals(240.0, incomplete.prefill.kcalPer100g!!, 0.0)
        assertFalse(incomplete.fromShop)
        assertTrue(sources.saved.isEmpty()) // nothing usable to cache
    }

    @Test
    fun `an incomplete Ukrainian product keeps its Ukrainian name`() {
        val result = lookUp(ua, FakeSources(off = mapOf(ua to incomplete(ua))))
        val prefill = (result as BarcodeLookup.Incomplete).prefill
        assertEquals("Хліб", prefill.name)
        assertEquals(BarcodeCountry.UKRAINE, prefill.country)
    }

    @Test
    fun `nothing anywhere is NotFound with the scanned digits, their origin and who was asked`() {
        val sources = FakeSources()
        assertEquals(BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE, shops = ShopsStatus.MISS), lookUp(ua, sources))
        assertEquals(listOf(ua), sources.asked)
        assertEquals(BarcodeLookup.NotFound(by, BarcodeCountry.BELARUS), lookUp(by, FakeSources()))
        assertEquals(BarcodeLookup.NotFound(uk, null), lookUp(uk, FakeSources()))
        // A UPC-A: the digits as scanned (the first candidate), not the padded form.
        assertEquals(BarcodeLookup.NotFound(upc, null, shops = ShopsStatus.MISS), lookUp(upc, FakeSources()))
    }

    @Test
    fun `no digits at all is NotFound with nothing to save under`() {
        val sources = FakeSources()
        assertEquals(BarcodeLookup.NotFound("", null), lookUp("no digits", sources))
        assertTrue(sources.asked.isEmpty())
        assertTrue(sources.shopsAsked.isEmpty())
    }

    // --- Busy, failed, offline --------------------------------------------

    @Test
    fun `busy is asked once more after a pause, and then found`() {
        val sources = FakeSources(
            off = mapOf(uk to OffAnswer.Busy(retryAfterSeconds = null)),
            offAgain = mapOf(uk to complete(uk)),
        )
        val result = lookUp(uk, sources)
        assertEquals("off:$uk", (result as BarcodeLookup.Found).product.id)
        assertEquals(listOf(uk, uk), sources.asked)
        assertEquals(listOf(2000L), pauses) // no Retry-After: two seconds
    }

    @Test
    fun `the pause follows Retry-After, at least one second`() {
        lookUp(uk, FakeSources(off = mapOf(uk to OffAnswer.Busy(3)), offAgain = mapOf(uk to complete(uk))))
        lookUp(uk, FakeSources(off = mapOf(uk to OffAnswer.Busy(0)), offAgain = mapOf(uk to complete(uk))))
        assertEquals(listOf(3000L, 1000L), pauses)
    }

    @Test
    fun `busy twice - the shops are still asked, and the message says OFF didn't answer`() {
        val sources = FakeSources(off = mapOf(ua to OffAnswer.Busy(null)))
        val result = lookUp(ua, sources)
        assertEquals(
            BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE, offUnavailable = true, shops = ShopsStatus.MISS),
            result,
        )
        assertEquals(LookupNotice.OFF_DOWN_SHOPS_MISS, (result as BarcodeLookup.NotFound).notice)
        assertEquals(listOf(ua, ua), sources.asked)
        assertEquals(listOf(ua14), sources.shopsAsked)
    }

    @Test
    fun `a Retry-After above five seconds is not waited for`() {
        val sources = FakeSources(off = mapOf(uk to OffAnswer.Busy(30)), offAgain = mapOf(uk to complete(uk)))
        assertEquals(BarcodeLookup.NotFound(uk, null, offUnavailable = true), lookUp(uk, sources))
        assertEquals(listOf(uk), sources.asked)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun `only one retry per lookup`() {
        val sources = FakeSources(
            off = mapOf(upcE to OffAnswer.Busy(null), upcEA to OffAnswer.Busy(null)),
            offAgain = mapOf(upcE to OffAnswer.Unknown),
        )
        val result = lookUp(upcE, sources) as BarcodeLookup.NotFound
        assertTrue(result.offUnavailable)
        assertEquals(listOf(upcE, upcE, upcEA), sources.asked)
        assertEquals(1, pauses.size)
    }

    @Test
    fun `a server error or a timeout stops asking - OFF unavailable, not offline`() {
        val sources = FakeSources(off = mapOf(upcE to OffAnswer.Failed, upcEA to complete(upcEA)))
        assertEquals(
            BarcodeLookup.NotFound(upcE, null, offUnavailable = true, shops = ShopsStatus.MISS),
            lookUp(upcE, sources),
        )
        assertEquals(listOf(upcE), sources.asked)
        val notAsked = lookUp(uk, FakeSources(off = mapOf(uk to OffAnswer.Failed))) as BarcodeLookup.NotFound
        assertEquals(LookupNotice.OFF_DOWN, notAsked.notice)
    }

    @Test
    fun `offline with nothing to show throws, so the user sees the offline message`() {
        val offline = OffAnswer.Offline(IOException("no network"))
        // Shops not asked (a UK code), or asked and silent: both throw.
        for (sources in listOf(
            FakeSources(off = mapOf(uk to offline)),
            FakeSources(off = mapOf(ua to offline), shops = ShopAnswer.Unavailable),
        )) {
            try {
                lookUp(sources.off.keys.single(), sources)
                fail("expected IOException")
            } catch (e: IOException) {
                assertEquals("no network", e.message)
            }
        }
    }

    @Test
    fun `offline, but a shop answered - that is a real not found`() {
        val sources = FakeSources(off = mapOf(ua to OffAnswer.Offline(IOException())), shops = ShopAnswer.Miss)
        assertEquals(
            BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE, offUnavailable = true, shops = ShopsStatus.MISS),
            lookUp(ua, sources),
        )
    }

    @Test
    fun `offline falls back to the stale cache row, or to what an earlier form gave`() {
        val stale = entity("off:$ua", fiber = null)
        val offline = OffAnswer.Offline(IOException())
        assertEquals(
            BarcodeLookup.Found(stale),
            lookUp(ua, FakeSources(cache = mapOf(ua to stale), off = mapOf(ua to offline))),
        )
        val result = lookUp(upcE, FakeSources(off = mapOf(upcE to incomplete(upcE), upcEA to offline)))
        assertTrue(result is BarcodeLookup.Incomplete)
    }

    // --- The Ukrainian shops ----------------------------------------------

    @Test
    fun `a usable shop product is saved under the scanned code and Found`() {
        val sources = FakeSources(shops = ShopAnswer.Hits(listOf(shopCard)))
        val product = (lookUp(ua, sources) as BarcodeLookup.Found).product
        assertEquals("zakaz:$ua", product.id)
        assertEquals(ProductSource.ZAKAZ, product.source)
        assertEquals("Нутелла паста горіхова 350г", product.name)
        assertEquals(539.0, product.kcalPer100g, 0.0)
        assertEquals(42L, product.cachedAtEpochMillis)
        assertFalse(product.isFavorite)
        assertEquals(listOf(product), sources.saved)
        assertEquals(listOf(ua14), sources.shopsAsked)
    }

    @Test
    fun `a star saved meanwhile under the shop id is kept`() {
        val sources = FakeSources(
            shops = ShopAnswer.Hits(listOf(shopCard)),
            duringShops = {
                shopCache[ua] = entity(FoodProductEntity.zakazId(ua), source = ProductSource.ZAKAZ, isFavorite = true)
            },
        )
        assertTrue((lookUp(ua, sources) as BarcodeLookup.Found).product.isFavorite)
    }

    @Test
    fun `the first usable card wins, in store order`() {
        val other = card(shopCard.toString().replace("Нутелла", "Інша"))
        val sources = FakeSources(shops = ShopAnswer.Hits(listOf(partialShopCard, shopCard, other)))
        assertEquals("Нутелла паста горіхова 350г", (lookUp(ua, sources) as BarcodeLookup.Found).product.name)
    }

    @Test
    fun `a usable shop product beats a partial one from OFF`() {
        val sources = FakeSources(off = mapOf(ua to incomplete(ua)), shops = ShopAnswer.Hits(listOf(shopCard)))
        assertEquals("zakaz:$ua", (lookUp(ua, sources) as BarcodeLookup.Found).product.id)
    }

    @Test
    fun `partial products - OFF's first, then the shop's, which the message credits to the shop`() {
        val both = FakeSources(off = mapOf(ua to incomplete(ua)), shops = ShopAnswer.Hits(listOf(partialShopCard)))
        val fromOff = lookUp(ua, both) as BarcodeLookup.Incomplete
        assertEquals("Хліб", fromOff.prefill.name)
        assertFalse(fromOff.fromShop)
        assertEquals(listOf(ua14), both.shopsAsked)

        val shopOnly = FakeSources(shops = ShopAnswer.Hits(listOf(partialShopCard)))
        val fromShop = lookUp(ua, shopOnly) as BarcodeLookup.Incomplete
        assertEquals("Печиво", fromShop.prefill.name)
        assertEquals(ua, fromShop.prefill.barcode)
        assertTrue(fromShop.fromShop)
        assertNull(fromShop.prefill.carbsPer100g)
        assertTrue(shopOnly.saved.isEmpty())
    }

    @Test
    fun `a bare OFF stub prefills nothing - the shop's prefill or a later form's wins`() {
        // OFF holds the code alone: no name, no value. The shop knows far more.
        val stub = OffAnswer.Product(ProductDto(code = ua))
        val wine = card(
            """{"title":"Вино біле сухе 0,75л","producer":{"trademark":"Виноробня Тест"},
               "volume":750,"nutrition_facts":{"ingredient_energy":"80ккал","ingredient_protein":"0г",
               "ingredient_fat":"0г"}}"""
        )
        val withShop = lookUp(ua, FakeSources(off = mapOf(ua to stub), shops = ShopAnswer.Hits(listOf(wine))))
        val fromShop = withShop as BarcodeLookup.Incomplete
        assertTrue(fromShop.fromShop)
        assertEquals("Виноробня Тест", fromShop.prefill.brand)
        assertEquals(80.0, fromShop.prefill.kcalPer100g!!, 0.0)
        assertTrue(fromShop.prefill.isLiquid)

        // Two OFF forms: an empty stub first, then a real partial record.
        val forms = FakeSources(off = mapOf(upcE to OffAnswer.Product(ProductDto(code = upcE)), upcEA to incomplete(upcEA)))
        val second = (lookUp(upcE, forms) as BarcodeLookup.Incomplete).prefill
        assertEquals("Хліб", second.name)
        assertEquals(upcEA, second.barcode)

        // A stub and nothing else: not found, and "Add" opens the form anyway.
        assertEquals(
            BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE, shops = ShopsStatus.MISS),
            lookUp(ua, FakeSources(off = mapOf(ua to stub))),
        )
    }

    @Test
    fun `a shop card with nothing usable in it counts as not listed`() {
        val sources = FakeSources(shops = ShopAnswer.Hits(listOf(card("{}"), card("""{"title":" "}"""))))
        assertEquals(BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE, shops = ShopsStatus.MISS), lookUp(ua, sources))
    }

    @Test
    fun `no shop answering is reported as such`() {
        val result = lookUp(ua, FakeSources(shops = ShopAnswer.Unavailable)) as BarcodeLookup.NotFound
        assertEquals(ShopsStatus.UNAVAILABLE, result.shops)
        assertEquals(LookupNotice.NOT_FOUND_SHOPS_DOWN, result.notice)
    }

    @Test
    fun `Belarusian and UK codes never go to the shops`() {
        for (code in listOf(by, uk, "50123452")) {
            val sources = FakeSources(shops = ShopAnswer.Hits(listOf(shopCard)))
            val result = lookUp(code, sources) as BarcodeLookup.NotFound
            assertEquals(ShopsStatus.NOT_ASKED, result.shops)
            assertTrue(sources.shopsAsked.isEmpty())
        }
    }

    @Test
    fun `the shops get the scanned code as GTIN-14`() {
        val upcSources = FakeSources()
        lookUp(upc, upcSources)
        assertEquals(listOf("00012345678905"), upcSources.shopsAsked)
        val ean8 = FakeSources()
        lookUp("40111445", ean8)
        assertEquals(listOf("40111445"), ean8.asked) // the padded form is the same code to OFF
        assertEquals(listOf("00000040111445"), ean8.shopsAsked)
    }

    // --- Pure helpers -----------------------------------------------------

    @Test
    fun `store replies - every listing in store order, else a miss, else no answer`() {
        val a = card("""{"title":"A"}""")
        val b = card("""{"title":"B"}""")
        assertEquals(
            ShopAnswer.Hits(listOf(a, b)),
            shopAnswerOf(listOf(StoreReply.NoAnswer, StoreReply.Listed(a), StoreReply.NotListed, StoreReply.Listed(b))),
        )
        assertEquals(ShopAnswer.Miss, shopAnswerOf(listOf(StoreReply.NoAnswer, StoreReply.NotListed)))
        assertEquals(ShopAnswer.Unavailable, shopAnswerOf(listOf(StoreReply.NoAnswer, StoreReply.NoAnswer)))
        assertEquals(ShopAnswer.Unavailable, shopAnswerOf(emptyList()))
    }

    @Test
    fun `Retry-After counts only as whole seconds`() {
        assertEquals(5L, retryAfterSeconds("5"))
        assertEquals(12L, retryAfterSeconds(" 12 "))
        assertNull(retryAfterSeconds("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(retryAfterSeconds("-1"))
        assertNull(retryAfterSeconds("+5"))
        assertEquals(Long.MAX_VALUE, retryAfterSeconds("99999999999999999999"))
        assertNull(retryAfterSeconds(""))
        assertNull(retryAfterSeconds(null))
    }
}
