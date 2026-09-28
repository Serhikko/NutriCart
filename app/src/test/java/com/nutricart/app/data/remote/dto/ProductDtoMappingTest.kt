package com.nutricart.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
