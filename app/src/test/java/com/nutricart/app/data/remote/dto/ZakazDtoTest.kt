package com.nutricart.app.data.remote.dto

import com.nutricart.app.domain.model.ProductSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zakaz.ua product cards and store list, and what the app makes of them.
 * Same vectors as the website's tests (Ukrainian shops).
 */
class ZakazDtoTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun s(text: String) = JsonPrimitive(text)

    private val code = "4823090100292"

    // --- Label values -----------------------------------------------------

    @Test
    fun `the first number of a label value, commas as decimal points`() {
        assertEquals(197.0, zakazNumber(s("197.00ккал"))!!, 0.0)
        assertEquals(9.95, zakazNumber(s("9,95г"))!!, 0.0)
        assertEquals(389.2, zakazNumber(s("389,2/1625,6"))!!, 0.0)
        assertEquals(0.0, zakazNumber(s(" 0 "))!!, 0.0)
        assertEquals(12.5, zakazNumber(JsonPrimitive(12.5))!!, 0.0)
    }

    @Test
    fun `no number, a negative one, or no value is unknown`() {
        assertNull(zakazNumber(s("")))
        assertNull(zakazNumber(s("—")))
        assertNull(zakazNumber(s("abc")))
        assertNull(zakazNumber(s("-3")))
        assertNull(zakazNumber(JsonNull))
        assertNull(zakazNumber(null))
        assertNull(zakazNumber(JsonPrimitive(true)))
        assertNull(zakazNumber(json("""{"value":1}""")))
    }

    @Test
    fun `energy in kJ only is converted, energy naming kcal is kept`() {
        assertEquals(388.4, zakazKcal(s("1625 кДж"))!!, 0.0)
        assertEquals(388.4, zakazKcal(s("1625kJ"))!!, 0.0)
        assertEquals(389.0, zakazKcal(s("389 ккал / 1628 кДж"))!!, 0.0)
        assertEquals(540.0, zakazKcal(s("540"))!!, 0.0)
    }

    @Test
    fun `energy above 900 kcal and macros above 100 g are nonsense`() {
        assertNull(zakazKcal(s("950ккал")))
        assertNull(zakazMacro(s("120г")))
        assertEquals(100.0, zakazMacro(s("100"))!!, 0.0)
        assertEquals(900.0, zakazKcal(s("900"))!!, 0.0)
    }

    @Test
    fun `energy must roughly match the macros, and the macros fit in 100 g`() {
        assertTrue(zakazConsistent(539.0, 6.3, 30.9, 57.5))
        assertTrue(zakazConsistent(0.0, 0.0, 0.0, 0.0))
        assertFalse(zakazConsistent(96.0, 2.9, 0.4, 3.6)) // spinach with another product's energy
        assertFalse(zakazConsistent(400.0, 50.0, 0.0, 56.0)) // 106 g of macros
    }

    @Test
    fun `a drink's stated strength counts the alcohol's energy`() {
        assertFalse(zakazConsistent(80.0, 0.0, 0.0, 0.3)) // wine, judged by 4/9/4 alone
        assertTrue(zakazConsistent(80.0, 0.0, 0.0, 0.3, abv = 13.0))
        assertTrue(zakazConsistent(225.0, 0.0, 0.0, 0.0, abv = 40.0)) // whisky
        // Only an extra way to pass: what passed without alcohol still does.
        assertTrue(zakazConsistent(539.0, 6.3, 30.9, 57.5, abv = 40.0))
        assertFalse(zakazConsistent(96.0, 2.9, 0.4, 3.6, abv = 0.0))

        assertEquals(13.0, zakazAbv("Вино біле сухе 13% 0,75л", isLiquid = true)!!, 0.0)
        assertEquals(4.6, zakazAbv("Пиво 0,5л 4,6 %", isLiquid = true)!!, 0.0)
        assertNull(zakazAbv("Шоколад чорний 70% 100г", isLiquid = false)) // not a drink
        assertNull(zakazAbv("Сік апельсиновий 100% 1л", isLiquid = true)) // past the 80 % cap
        assertNull(zakazAbv("Вода 0,5л", isLiquid = true))
        assertNull(zakazAbv(null, isLiquid = true))
    }

    // --- Product cards ----------------------------------------------------

    private val v1 = """{"title":" Нутелла  паста горіхова 350г ","producer":{"trademark":"Nutella"},
        "weight":350,"volume":null,"unit":"pcs",
        "nutrition_facts":{"ingredient_energy":"539.00ккал","ingredient_protein":"6,3г",
                           "ingredient_fat":"30,9г","ingredient_carbohydrates":"57,5г"}}"""

    private fun usable(card: String): ZakazMatch.Usable {
        val match = mapZakazCard(json(card), code)
        assertTrue("expected a usable product, got $match", match is ZakazMatch.Usable)
        return match as ZakazMatch.Usable
    }

    private fun partial(card: String): ZakazMatch.Partial {
        val match = mapZakazCard(json(card), code)
        assertTrue("expected a partial product, got $match", match is ZakazMatch.Partial)
        return match as ZakazMatch.Partial
    }

    @Test
    fun `V1 - a complete card is usable, name cleaned`() {
        val p = usable(v1).prefill
        assertEquals("Нутелла паста горіхова 350г", p.name)
        assertEquals("Nutella", p.brand)
        assertEquals(539.0, p.kcalPer100g!!, 0.0)
        assertEquals(6.3, p.proteinPer100g!!, 0.0)
        assertEquals(30.9, p.fatPer100g!!, 0.0)
        assertEquals(57.5, p.carbsPer100g!!, 0.0)
        assertFalse(p.isLiquid)
        assertEquals(code, p.barcode)
        assertNull(p.servingSizeG) // "weight" is the pack, not a portion
        assertNull(p.fiberPer100g)
        assertNull(p.sugarsPer100g)
        assertNull(p.saltPer100g)
        assertNull(p.saturatedFatPer100g)
    }

    @Test
    fun `V2 - a card wrapped as product maps the same`() {
        assertEquals(usable(v1), usable("""{"product":$v1}"""))
    }

    @Test
    fun `V3 - water stated as a volume is a usable drink of zeros`() {
        val p = usable(
            """{"title":"Вода питна негазована 0,5л","unit":"pcs","volume":500,
               "nutrition_facts":{"ingredient_energy":"0ккал","ingredient_protein":"0г",
                                  "ingredient_fat":"0г","ingredient_carbohydrates":"0г"}}"""
        ).prefill
        assertTrue(p.isLiquid)
        assertEquals(0.0, p.kcalPer100g!!, 0.0)
        assertEquals(0.0, p.proteinPer100g!!, 0.0)
        assertEquals(0.0, p.fatPer100g!!, 0.0)
        assertEquals(0.0, p.carbsPer100g!!, 0.0)
    }

    @Test
    fun `V4 - a drink told by the pack size in its title`() {
        val p = usable(
            """{"title":"Молоко 2,5% 900мл","volume":null,"unit":"pcs",
               "nutrition_facts":{"ingredient_energy":"52ккал","ingredient_protein":"2,8",
                                  "ingredient_fat":"2,5","ingredient_carbohydrates":"4,7"}}"""
        ).prefill
        assertTrue(p.isLiquid)
        assertEquals(52.0, p.kcalPer100g!!, 0.0)
    }

    @Test
    fun `V5 - a missing value prefills the form with the rest`() {
        val p = partial(v1.replace(""","ingredient_carbohydrates":"57,5г"""", "")).prefill
        assertEquals("Нутелла паста горіхова 350г", p.name)
        assertEquals("Nutella", p.brand)
        assertEquals(539.0, p.kcalPer100g!!, 0.0)
        assertEquals(6.3, p.proteinPer100g!!, 0.0)
        assertEquals(30.9, p.fatPer100g!!, 0.0)
        assertNull(p.carbsPer100g)
    }

    @Test
    fun `V6 - values that don't add up prefill the form, all of them`() {
        val p = partial(
            """{"title":"Шпинат","unit":"kg",
               "nutrition_facts":{"ingredient_energy":"96ккал","ingredient_protein":"2,9",
                                  "ingredient_fat":"0,4","ingredient_carbohydrates":"3,6"}}"""
        ).prefill
        assertEquals(96.0, p.kcalPer100g!!, 0.0)
        assertEquals(2.9, p.proteinPer100g!!, 0.0)
        assertEquals(0.4, p.fatPer100g!!, 0.0)
        assertEquals(3.6, p.carbsPer100g!!, 0.0)
        assertFalse(p.isLiquid)
        // Nothing is missing: the form asks for a check of the numbers.
        assertTrue(p.hasCoreValues)
        assertFalse(partial(v1.replace(""","ingredient_carbohydrates":"57,5г"""", "")).prefill.hasCoreValues)
    }

    @Test
    fun `wine and spirits with correct labels are usable`() {
        val wine = usable(
            """{"title":"Вино біле сухе 13% 0,75л","producer":{"trademark":"Виноробня Тест"},
               "volume":750,"unit":"pcs",
               "nutrition_facts":{"ingredient_energy":"80ккал","ingredient_protein":"0",
                                  "ingredient_fat":"0","ingredient_carbohydrates":"0,3"}}"""
        ).prefill
        assertTrue(wine.isLiquid)
        assertEquals(80.0, wine.kcalPer100g!!, 0.0)
        usable(
            """{"title":"Віскі купажований 40% 0,7л","volume":700,"unit":"pcs",
               "nutrition_facts":{"ingredient_energy":"225ккал","ingredient_protein":"0",
                                  "ingredient_fat":"0","ingredient_carbohydrates":"0"}}"""
        )
        // The strength of a food is no excuse: still values that don't add up.
        partial(
            """{"title":"Шоколад чорний 70% 100г","unit":"pcs",
               "nutrition_facts":{"ingredient_energy":"80ккал","ingredient_protein":"0",
                                  "ingredient_fat":"0","ingredient_carbohydrates":"0,3"}}"""
        )
    }

    @Test
    fun `V7 - energy in kJ is converted and the card stays usable`() {
        val p = usable(v1.replace("539.00ккал", "2252 кДж")).prefill
        assertEquals(538.2, p.kcalPer100g!!, 0.0)
    }

    @Test
    fun `V8 - a card with no name and no value is nothing`() {
        assertNull(mapZakazCard(json("{}"), code))
        assertNull(mapZakazCard(json("""{"title":"  ","nutrition_facts":{}}"""), code))
        assertNull(mapZakazCard(json("[]"), code))
        assertNull(mapZakazCard(JsonNull, code))
    }

    @Test
    fun `V9 - unbranded goods have no brand`() {
        assertNull(usable(v1.replace("\"Nutella\"", "\"без тм\"")).prefill.brand)
        assertNull(usable(v1.replace("\"Nutella\"", "\"Без ТМ\"")).prefill.brand)
        assertNull(usable(v1.replace("\"Nutella\"", "\"без торгової марки\"")).prefill.brand)
    }

    @Test
    fun `V10 - a soft drink with a volume is a usable drink`() {
        val p = usable(
            """{"title":"Напій сильногазований 1,75л","volume":1750,"unit":"pcs",
               "nutrition_facts":{"ingredient_energy":"42ккал","ingredient_protein":"0",
                                  "ingredient_fat":"0","ingredient_carbohydrates":"10,6"}}"""
        ).prefill
        assertTrue(p.isLiquid)
        assertEquals(10.6, p.carbsPer100g!!, 0.0)
    }

    @Test
    fun `a name alone, or values alone, still prefill the form`() {
        assertEquals("Хліб", partial("""{"title":"Хліб"}""").prefill.name)
        val p = partial("""{"nutrition_facts":{"ingredient_energy":"240ккал"}}""").prefill
        assertNull(p.name)
        assertEquals(240.0, p.kcalPer100g!!, 0.0)
    }

    @Test
    fun `a usable card becomes a read-only shop row under the scanned code`() {
        val e = usable(v1).toEntity(cachedAtEpochMillis = 7L, isFavorite = true)
        assertEquals("zakaz:$code", e.id)
        assertEquals(ProductSource.ZAKAZ, e.source)
        assertEquals("Нутелла паста горіхова 350г", e.name)
        assertEquals(539.0, e.kcalPer100g, 0.0)
        assertEquals(7L, e.cachedAtEpochMillis)
        assertTrue(e.isFavorite)
        assertNull(e.fiberPer100g)
        assertNull(e.servingSizeG)
    }

    // --- The store list ---------------------------------------------------

    @Test
    fun `one active store per chain, a Kyiv one first, in chain order`() {
        val stores = json(
            """[{"id":"1","retail_chain":"novus","city":"lviv","is_active":true},
                {"id":"2","retail_chain":"Novus","city":"kiev","is_active":true},
                {"id":"3","retail_chain":"auchan","city":"kiev","is_active":false},
                {"id":"4","retail_chain":"auchan","city":"dnipro","is_active":true},
                {"id":"5","retail_chain":"metro","city":null,"is_active":true},
                {"id":"6","retail_chain":"silpo","city":"kiev","is_active":true}]"""
        )
        assertEquals(listOf("4", "2", "5"), pickZakazStores(stores))
    }

    @Test
    fun `at most six stores, and only real ids of really active stores`() {
        val all = ZAKAZ_CHAINS.mapIndexed { i, chain ->
            """{"id":"$i","retail_chain":"$chain","city":"Київ","is_active":true}"""
        }
        assertEquals(listOf("0", "1", "2", "3", "4", "5"), pickZakazStores(json(all.joinToString(",", "[", "]"))))
        // The shops' HTTP client lets exactly this many run at once (NetworkModule).
        assertEquals(6, ZAKAZ_MAX_STORES)
        val odd = json(
            """[{"id":1,"retail_chain":"auchan","is_active":true},
                {"id":"","retail_chain":"auchan","is_active":true},
                {"id":"2","retail_chain":"novus","is_active":"true"},
                {"id":"3","retail_chain":"metro"}]"""
        )
        assertEquals(emptyList<String>(), pickZakazStores(odd))
    }

    @Test
    fun `anything but a list is no stores, and the caller falls back`() {
        assertEquals(emptyList<String>(), pickZakazStores(json("""{"results":[]}""")))
        assertEquals(emptyList<String>(), pickZakazStores(null))
        assertEquals(5, ZAKAZ_FALLBACK_STORES.size)
    }
}
