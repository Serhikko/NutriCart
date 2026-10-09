package com.nutricart.app.data.repository

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.remote.dto.NutrimentsDto
import com.nutricart.app.data.remote.dto.ProductDto
import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.ProductSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * The order of the barcode checks (the website's lookup follows the same
 * order): the user's own product, a fresh cache row, Open Food Facts form by
 * form, then the fallbacks.
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
        /** Codes missing here answer Unknown (a 404). */
        val off: Map<String, OffAnswer> = emptyMap(),
    ) : BarcodeLookupSources {
        val asked = mutableListOf<String>()
        val saved = mutableListOf<FoodProductEntity>()

        override suspend fun ownProduct(code: String): FoodProductEntity? = own[code]
        override suspend fun cachedOffProduct(code: String): FoodProductEntity? = cache[code]
        override suspend fun askOff(code: String): OffAnswer {
            asked += code
            return off[code] ?: OffAnswer.Unknown
        }
        override suspend fun save(product: FoodProductEntity) {
            saved += product
        }
    }

    private fun lookUp(scanned: String, sources: FakeSources): BarcodeLookup =
        runSuspend { lookUpBarcode(scanned, sources, nowMillis = { 42L }) }

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

    private val ua = "4820024700016"

    // 12-digit UPC-A: candidates are the 12 digits, then the 13-digit padded form.
    private val upc = "012345678905"
    private val upcPadded = "0012345678905"

    @Test
    fun `the user's own product wins over Open Food Facts and is not even asked`() {
        val mine = entity(FoodProductEntity.localBarcodeId(ua), "Мій хліб", ProductSource.LOCAL)
        val sources = FakeSources(own = mapOf(ua to mine), off = mapOf(ua to complete(ua)))
        assertEquals(BarcodeLookup.Found(mine), lookUp(ua, sources))
        assertTrue(sources.asked.isEmpty())
    }

    @Test
    fun `the user's own product is found under any form of the code`() {
        val mine = entity(FoodProductEntity.localBarcodeId(upcPadded), source = ProductSource.LOCAL)
        val sources = FakeSources(own = mapOf(upcPadded to mine))
        assertEquals(BarcodeLookup.Found(mine), lookUp(upc, sources))
    }

    @Test
    fun `an incomplete form does not stop the search - a complete one is Found and cached`() {
        val sources = FakeSources(off = mapOf(upc to incomplete(upc), upcPadded to complete(upcPadded)))
        val result = lookUp(upc, sources)
        assertTrue(result is BarcodeLookup.Found)
        val product = (result as BarcodeLookup.Found).product
        assertEquals("off:$upcPadded", product.id)
        assertEquals(42L, product.cachedAtEpochMillis)
        assertEquals(listOf(product), sources.saved)
        assertEquals(listOf(upc, upcPadded), sources.asked)
    }

    @Test
    fun `only incomplete forms - the first one prefills the form`() {
        val sources = FakeSources(
            off = mapOf(upc to incomplete(upc, "Перший"), upcPadded to incomplete(upcPadded, "Другий")),
        )
        val result = lookUp(upc, sources)
        assertTrue(result is BarcodeLookup.Incomplete)
        val prefill = (result as BarcodeLookup.Incomplete).prefill
        assertEquals(upc, prefill.barcode)
        assertEquals(240.0, prefill.kcalPer100g!!, 0.0)
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
    fun `nothing anywhere is NotFound with the scanned digits and their origin`() {
        val sources = FakeSources()
        assertEquals(BarcodeLookup.NotFound(ua, BarcodeCountry.UKRAINE), lookUp(ua, sources))
        assertEquals(listOf(ua), sources.asked)
        assertEquals(
            BarcodeLookup.NotFound("4810268000013", BarcodeCountry.BELARUS),
            lookUp("4810268000013", FakeSources()),
        )
        assertEquals(
            BarcodeLookup.NotFound("5000112637922", null),
            lookUp("5000112637922", FakeSources()),
        )
        // A UPC-A: the digits as scanned (the first candidate), not the padded form.
        assertEquals(BarcodeLookup.NotFound(upc, null), lookUp(upc, FakeSources()))
    }

    @Test
    fun `no digits at all is NotFound with nothing to save under`() {
        assertEquals(BarcodeLookup.NotFound("", null), lookUp("no digits", FakeSources()))
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
        assertEquals(BarcodeLookup.Found(stale), lookUp(ua, FakeSources(cache = mapOf(ua to stale))))
    }

    @Test
    fun `offline with nothing to show throws, so the user sees the offline message`() {
        val sources = FakeSources(off = mapOf(ua to OffAnswer.Offline(IOException("no network"))))
        try {
            lookUp(ua, sources)
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("no network", e.message)
        }
    }

    @Test
    fun `offline falls back to the stale cache row, or to what an earlier form gave`() {
        val stale = entity("off:$ua", fiber = null)
        val offline = OffAnswer.Offline(IOException())
        assertEquals(
            BarcodeLookup.Found(stale),
            lookUp(ua, FakeSources(cache = mapOf(ua to stale), off = mapOf(ua to offline))),
        )
        val result = lookUp(upc, FakeSources(off = mapOf(upc to incomplete(upc), upcPadded to offline)))
        assertTrue(result is BarcodeLookup.Incomplete)
    }

    @Test
    fun `a server error stops asking - NotFound when there is nothing else`() {
        val sources = FakeSources(off = mapOf(upc to OffAnswer.Failed, upcPadded to complete(upcPadded)))
        assertEquals(BarcodeLookup.NotFound(upc, null), lookUp(upc, sources))
        assertEquals(listOf(upc), sources.asked)
    }
}
