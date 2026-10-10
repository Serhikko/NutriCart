package com.nutricart.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world shapes of Open Food Facts answers, and what the app must make
 * of them. Each case here was a product that used to be dropped or that
 * used to break a whole search page.
 */
class ProductDtoMappingTest {

    // The same Json configuration as NetworkModule.provideJson().
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun product(body: String): ProductDto = json.decodeFromString(ProductDto.serializer(), body)

    @Test
    fun `a complete product maps as before`() {
        val p = product(
            """{"code":"5000112637922","product_name":"Baked Beans","brands":"Heinz, Kraft",
               "serving_quantity":"207","additives_tags":["en:e412"],
               "nutriments":{"energy-kcal_100g":81,"proteins_100g":4.7,"fat_100g":0.2,"carbohydrates_100g":12.9,
                             "fiber_100g":3.7,"sugars_100g":4.7,"salt_100g":0.6,"saturated-fat_100g":0.1}}"""
        )
        val e = checkNotNull(p.toEntityOrNull(1L))
        assertEquals("off:5000112637922", e.id)
        assertEquals("Heinz", e.brand)
        assertEquals(81.0, e.kcalPer100g, 0.001)
        assertEquals(207.0, e.servingSizeG!!, 0.001)
        assertEquals("E412", e.additivesCsv)
    }

    @Test
    fun `kJ-only energy is converted instead of dropping the product`() {
        val p = product(
            """{"code":"1","product_name":"Oatcakes",
               "nutriments":{"energy-kj_100g":1841,"proteins_100g":10,"fat_100g":18,"carbohydrates_100g":60}}"""
        )
        val e = checkNotNull(p.toEntityOrNull(1L))
        assertEquals(440.0, e.kcalPer100g, 0.5)
    }

    @Test
    fun `OFF's generic energy field counts as kJ`() {
        val p = product(
            """{"code":"1","product_name":"Crisps",
               "nutriments":{"energy_100g":2200,"proteins_100g":6,"fat_100g":30,"carbohydrates_100g":50}}"""
        )
        assertEquals(525.8, checkNotNull(p.toEntityOrNull(1L)).kcalPer100g, 0.1)
    }

    @Test
    fun `per-serving-only labels are rescaled to 100 g`() {
        // A 30 g pack whose contributor typed the "per pack" column only.
        val p = product(
            """{"code":"2","product_name":"Cereal bar","serving_quantity":30,
               "nutriments":{"energy-kcal_serving":120,"proteins_serving":3,"fat_serving":4.5,
                             "carbohydrates_serving":18,"sugars_serving":9}}"""
        )
        val e = checkNotNull(p.toEntityOrNull(1L))
        assertEquals(400.0, e.kcalPer100g, 0.001)
        assertEquals(10.0, e.proteinPer100g, 0.001)
        assertEquals(15.0, e.fatPer100g, 0.001)
        assertEquals(60.0, e.carbsPer100g, 0.001)
        assertEquals(30.0, e.sugarsPer100g!!, 0.001)
        assertNull(e.fiberPer100g) // not stated anywhere -> stays unknown
    }

    @Test
    fun `per-serving values without a serving size cannot be used`() {
        val p = product(
            """{"code":"2","product_name":"Bar",
               "nutriments":{"energy-kcal_serving":120,"proteins_serving":3,"fat_serving":4.5,"carbohydrates_serving":18}}"""
        )
        assertNull(p.toEntityOrNull(1L))
    }

    @Test
    fun `missing energy is computed from the macros as a last resort`() {
        val p = product(
            """{"code":"3","product_name":"Rice",
               "nutriments":{"proteins_100g":7,"fat_100g":1,"carbohydrates_100g":78}}"""
        )
        // 28 + 9 + 312
        assertEquals(349.0, checkNotNull(p.toEntityOrNull(1L)).kcalPer100g, 0.001)
    }

    @Test
    fun `a missing macro still drops the product`() {
        val p = product(
            """{"code":"4","product_name":"Mystery","nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"fat_100g":1}}"""
        )
        assertNull(p.toEntityOrNull(1L))
    }

    @Test
    fun `English name is the fallback for a blank main name`() {
        val p = product(
            """{"code":"5","product_name":"","product_name_en":"Hummus",
               "nutriments":{"energy-kcal_100g":300,"proteins_100g":8,"fat_100g":25,"carbohydrates_100g":10}}"""
        )
        assertEquals("Hummus", checkNotNull(p.toEntityOrNull(1L)).name)
    }

    @Test
    fun `malformed nutriment values become unknown instead of failing the decode`() {
        val p = product(
            """{"code":"6","product_name":"Yoghurt",
               "nutriments":{"energy-kcal_100g":"95","proteins_100g":"4,2","fat_100g":3.5,"carbohydrates_100g":12,
                             "sugars_100g":"","fiber_100g":"<0.5","salt_100g":null}}"""
        )
        val e = checkNotNull(p.toEntityOrNull(1L))
        assertEquals(95.0, e.kcalPer100g, 0.001)
        assertEquals(4.2, e.proteinPer100g, 0.001)
        assertNull(e.sugarsPer100g)
        assertNull(e.fiberPer100g)
        assertNull(e.saltPer100g)
    }

    @Test
    fun `one broken product does not break the search page`() {
        val page = json.decodeFromString(
            SearchResponseDto.serializer(),
            """{"products":[
                 {"code":"7","product_name":"Broken","nutriments":{"energy-kcal_100g":"n/a"}},
                 {"code":"8","product_name":"Fine","nutriments":{"energy-kcal_100g":50,"proteins_100g":1,"fat_100g":1,"carbohydrates_100g":10}}
               ]}"""
        )
        val usable = page.products.mapNotNull { it.toEntityOrNull(1L) }
        assertEquals(listOf("off:8"), usable.map { it.id })
    }

    @Test
    fun `lenient parser accepts numbers, numeric strings and commas`() {
        assertEquals(12.5, LenientDoubleSerializer.parse("12.5")!!, 0.0)
        assertEquals(12.5, LenientDoubleSerializer.parse(" 12,5 ")!!, 0.0)
        assertNull(LenientDoubleSerializer.parse(""))
        assertNull(LenientDoubleSerializer.parse("<0.5"))
        assertNull(LenientDoubleSerializer.parse("NaN"))
    }

    @Test
    fun `a drink is flagged from its pack size or its per-100ml basis, food is not`() {
        val drink = product(
            """{"code":"3","product_name":"Energy drink","quantity":"500 ml","nutrition_data_per":"100ml",
               "nutriments":{"energy-kcal_100g":3,"proteins_100g":0,"fat_100g":0,"carbohydrates_100g":0.7}}"""
        ).toEntityOrNull(0L)!!
        assertEquals(true, drink.isLiquid)
        val food = product(
            """{"code":"4","product_name":"Rice","quantity":"1 kg",
               "nutriments":{"energy-kcal_100g":350,"proteins_100g":7,"fat_100g":1,"carbohydrates_100g":78}}"""
        ).toEntityOrNull(0L)!!
        assertEquals(false, food.isLiquid)
    }

    // --- Names: the shared vectors (same as the website's tests) ----------

    private fun nameOf(body: String): String? = checkNotNull(product(body).toPrefill()).name

    @Test
    fun `name vector 1 - a Ukrainian product named only in Ukrainian`() {
        assertEquals(
            "Молоко 2,5%",
            nameOf("""{"code":"4820024700016","product_name":"","product_name_uk":"Молоко 2,5%"}"""),
        )
    }

    @Test
    fun `name vector 2 - Ukrainian barcode, Ukrainian name before English`() {
        assertEquals(
            "Молоко",
            nameOf("""{"code":"4820024700016","product_name":"","product_name_en":"Milk","product_name_uk":"Молоко"}"""),
        )
    }

    @Test
    fun `name vector 3 - other barcodes keep English first`() {
        assertEquals(
            "Milk",
            nameOf("""{"code":"5000112637922","product_name":"","product_name_en":"Milk","product_name_uk":"Молоко"}"""),
        )
    }

    @Test
    fun `name vector 4 - Belarusian barcode, Belarusian name before Russian`() {
        assertEquals(
            "Сырок беларускі",
            nameOf("""{"code":"4810268000013","product_name":"","product_name_ru":"Сырок","product_name_be":"Сырок беларускі"}"""),
        )
    }

    @Test
    fun `name vector 5 - the main name always wins`() {
        assertEquals(
            "Kefir",
            nameOf("""{"code":"4820024700016","product_name":"Kefir","product_name_uk":"Кефір"}"""),
        )
    }

    @Test
    fun `name vector 6 - a generic name when no product name exists`() {
        assertEquals(
            "Сир кисломолочний",
            nameOf("""{"code":"4820024700016","generic_name":"","generic_name_uk":"Сир кисломолочний"}"""),
        )
    }

    @Test
    fun `name vector 7 - whitespace runs collapse and the ends are trimmed`() {
        assertEquals(
            "Хліб житній",
            nameOf("""{"code":"4820024700016","product_name":"  Хліб\n  житній  "}"""),
        )
    }

    @Test
    fun `name vector 8 - a blank main name falls through to the next language`() {
        assertEquals(
            "Хлеб",
            nameOf("""{"code":"4820024700016","product_name":"   ","product_name_ru":"Хлеб"}"""),
        )
    }

    @Test
    fun `a complete Ukrainian product named only in Ukrainian is no longer dropped`() {
        val e = checkNotNull(
            product(
                """{"code":"4820024700016","product_name":"","product_name_uk":"Кефір 2,5%","quantity":"900 мл",
                   "nutriments":{"energy-kcal_100g":53,"proteins_100g":3,"fat_100g":2.5,"carbohydrates_100g":4}}"""
            ).toEntityOrNull(1L)
        )
        assertEquals("Кефір 2,5%", e.name)
        assertTrue(e.isLiquid) // "900 мл"
    }

    // --- Incomplete products: the prefill --------------------------------

    @Test
    fun `an incomplete product is dropped by the full mapping but prefills the form`() {
        val p = product(
            """{"code":"4820024700016","product_name_uk":"Хліб","brands":"Київхліб, Інше",
               "nutriments":{"energy-kcal_100g":240,"proteins_100g":8}}"""
        )
        assertNull(p.toEntityOrNull(1L))
        val prefill = checkNotNull(p.toPrefill("4820024700016"))
        assertEquals("4820024700016", prefill.barcode)
        assertEquals("Хліб", prefill.name)
        assertEquals("Київхліб", prefill.brand)
        assertEquals(240.0, prefill.kcalPer100g!!, 0.0)
        assertEquals(8.0, prefill.proteinPer100g!!, 0.0)
        assertNull(prefill.fatPer100g)
        assertNull(prefill.carbsPer100g)
        assertNull(prefill.servingSizeG)
        assertFalse(prefill.isLiquid)
    }

    @Test
    fun `the prefill resolves values exactly like the full mapping`() {
        // Per-serving only, kJ only, and a pack size in litres — but no name.
        val p = product(
            """{"code":"4820000000001","serving_quantity":"250","quantity":"1 л",
               "nutriments":{"energy-kj_serving":523,"proteins_serving":7.5,"fat_serving":6.25,
                             "carbohydrates_serving":10,"sugars_serving":10}}"""
        )
        assertNull(p.toEntityOrNull(1L)) // no name
        val prefill = checkNotNull(p.toPrefill(null))
        assertNull(prefill.name)
        assertEquals(50.0, prefill.kcalPer100g!!, 0.1) // 523 kJ / 4.184 per 250 ml
        assertEquals(3.0, prefill.proteinPer100g!!, 0.001)
        assertEquals(2.5, prefill.fatPer100g!!, 0.001)
        assertEquals(4.0, prefill.carbsPer100g!!, 0.001)
        assertEquals(4.0, prefill.sugarsPer100g!!, 0.001)
        assertEquals(250.0, prefill.servingSizeG!!, 0.0)
        assertTrue(prefill.isLiquid)

        // The same product WITH a name maps to the same numbers.
        val named = product(
            """{"code":"4820000000001","product_name":"Молоко","serving_quantity":"250","quantity":"1 л",
               "nutriments":{"energy-kj_serving":523,"proteins_serving":7.5,"fat_serving":6.25,
                             "carbohydrates_serving":10,"sugars_serving":10}}"""
        ).toEntityOrNull(1L)!!
        assertEquals(prefill.kcalPer100g!!, named.kcalPer100g, 0.0)
        assertEquals(prefill.proteinPer100g!!, named.proteinPer100g, 0.0)
        assertEquals(prefill.sugarsPer100g, named.sugarsPer100g)
        assertTrue(named.isLiquid)
    }

    @Test
    fun `the prefill uses the looked-up code when OFF gives none`() {
        val p = product("""{"product_name":"Сирок","nutriments":{}}""")
        assertEquals("4810268000013", checkNotNull(p.toPrefill("4810268000013")).barcode)
        assertNull(p.toPrefill(null)) // no code anywhere: nothing to save it under
        // The looked-up code also decides the name's language order.
        val q = product("""{"product_name_ru":"Сырок","product_name_be":"Сырок беларускі"}""")
        assertEquals("Сырок беларускі", checkNotNull(q.toPrefill("4810268000013")).name)
    }

    // --- OFF's estimates from the ingredients ----------------------------

    @Test
    fun `a named product with only estimated values is not found, but prefills the form`() {
        val p = product(
            """{"code":"4820000000002","product_name_uk":"Печиво",
               "nutriments":{},
               "nutriments_estimated":{"energy-kcal_100g":452,"proteins_100g":7.1,"fat_100g":18.2,
                                       "carbohydrates_100g":64.5,"sugars_100g":21,"fiber_100g":2.4}}"""
        )
        assertNull(p.toEntityOrNull(1L)) // estimates never make a product "found"
        val prefill = checkNotNull(p.toPrefill("4820000000002"))
        assertEquals("Печиво", prefill.name)
        assertEquals(452.0, prefill.kcalPer100g!!, 0.0)
        assertEquals(7.1, prefill.proteinPer100g!!, 0.0)
        assertEquals(18.2, prefill.fatPer100g!!, 0.0)
        assertEquals(64.5, prefill.carbsPer100g!!, 0.0)
        assertEquals(21.0, prefill.sugarsPer100g!!, 0.0)
        assertEquals(2.4, prefill.fiberPer100g!!, 0.0)
        assertNull(prefill.saltPer100g) // estimated nowhere: stays unknown
    }

    @Test
    fun `stated values always beat the estimates`() {
        val p = product(
            """{"code":"4820000000003","product_name":"Сир",
               "nutriments":{"energy-kcal_100g":350,"proteins_100g":25},
               "nutriments_estimated":{"energy-kcal_100g":300,"proteins_100g":20,"fat_100g":27,
                                       "carbohydrates_100g":1}}"""
        )
        assertNull(p.toEntityOrNull(1L))
        val prefill = checkNotNull(p.toPrefill(null))
        assertEquals(350.0, prefill.kcalPer100g!!, 0.0)
        assertEquals(25.0, prefill.proteinPer100g!!, 0.0)
        assertEquals(27.0, prefill.fatPer100g!!, 0.0) // estimated: missing on the label
        assertEquals(1.0, prefill.carbsPer100g!!, 0.0)
    }

    @Test
    fun `estimates that are missing or not numbers change nothing`() {
        // The same vectors as the website's offNames test.
        assertNull(checkNotNull(product("""{"code":"4820000000004","nutriments_estimated":"n/a"}""").toPrefill()).kcalPer100g)
        assertNull(
            checkNotNull(product("""{"code":"4820000000004","nutriments_estimated":{"fat_100g":"x"}}""").toPrefill())
                .fatPer100g
        )
    }

    @Test
    fun `an odd estimates value never costs a complete product`() {
        // Decoded as the scanner does, the whole answer at once: a failure here
        // used to read as "Open Food Facts didn't answer".
        for (odd in listOf("\"n/a\"", "[]", "5", "null")) {
            val answer = json.decodeFromString(
                ProductResponseDto.serializer(),
                """{"status":1,"product":{"code":"1","product_name":"Oats",
                   "nutriments":{"energy-kcal_100g":370,"proteins_100g":13,"fat_100g":7,"carbohydrates_100g":60},
                   "nutriments_estimated":$odd}}""",
            )
            val p = checkNotNull(answer.product)
            assertNull(odd, p.nutrimentsEstimated)
            assertEquals(odd, 370.0, checkNotNull(p.toEntityOrNull(1L)).kcalPer100g, 0.0)
        }
    }

    @Test
    fun `the prefill says when a core value is only an estimate`() {
        val estimatedOnly = product(
            """{"code":"4820000000005","product_name_uk":"Печиво",
               "nutriments_estimated":{"energy-kcal_100g":452,"proteins_100g":7.1,"fat_100g":18.2,
                                       "carbohydrates_100g":64.5}}"""
        )
        val prefill = checkNotNull(estimatedOnly.toPrefill())
        assertTrue(prefill.estimated)
        assertTrue(prefill.hasCoreValues) // nothing missing: the form asks for a check

        // A detail nutrient estimated, the core stated: nothing core to check.
        val detailOnly = product(
            """{"code":"4820000000006",
               "nutriments":{"energy-kcal_100g":350,"proteins_100g":25,"fat_100g":27,"carbohydrates_100g":1},
               "nutriments_estimated":{"energy-kcal_100g":300,"fiber_100g":2}}"""
        )
        assertFalse(checkNotNull(detailOnly.toPrefill()).estimated)
        assertFalse(checkNotNull(product("""{"code":"4820000000007","product_name":"Сир"}""").toPrefill()).estimated)
    }

    @Test
    fun `a complete product ignores the estimates altogether`() {
        val p = product(
            """{"code":"1","product_name":"Oats",
               "nutriments":{"energy-kcal_100g":370,"proteins_100g":13,"fat_100g":7,"carbohydrates_100g":60},
               "nutriments_estimated":{"energy-kcal_100g":999,"fiber_100g":10}}"""
        )
        val e = checkNotNull(p.toEntityOrNull(1L))
        assertEquals(370.0, e.kcalPer100g, 0.0)
        assertNull(e.fiberPer100g) // an estimate is not a stated value
    }

    // --- The requested field list ----------------------------------------

    @Test
    fun `every field ProductDto reads is requested from Open Food Facts`() {
        // OFF returns ONLY the requested fields: a field missing from the list
        // would silently be null in every response.
        val requested = OFF_PRODUCT_FIELDS.split(",").toSet()
        val descriptor = ProductDto.serializer().descriptor
        val declared = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        for (name in declared) assertTrue("$name is not in OFF_PRODUCT_FIELDS", name in requested)
        for (name in listOf(
            "product_name_uk", "product_name_ru", "product_name_be",
            "generic_name", "generic_name_en", "generic_name_uk", "generic_name_ru", "generic_name_be",
            "nutriments_estimated",
        )) {
            assertTrue(name, name in requested)
        }
        // Search asks for the same, except the estimates only the scanner's form reads.
        val searched = OFF_SEARCH_FIELDS.split(",").toSet()
        assertEquals(requested - "nutriments_estimated", searched)
    }
}
