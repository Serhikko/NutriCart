package com.nutricart.app.data.remote.dto

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.LiquidDetector
import com.nutricart.app.domain.logic.NutritionLabelMath
import com.nutricart.app.domain.logic.ProductNames
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ProductSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * The zakaz.ua stores API: the shared backend behind the online shops of
 * Auchan, Novus, METRO, EKO Market and other Ukrainian chains. It is
 * unofficial and undocumented, so its answers are read as untyped JSON and
 * every field is distrusted: a value that does not parse, or is out of
 * range, is simply unknown. The website reads the same fields by the same
 * rules, checked by the same test vectors.
 *
 * Nutrition comes as label text per 100 g (per 100 ml for a drink):
 * "197.00ккал", "9,95г", "389,2/1625,6", now and then kJ ("2252 кДж").
 * Detail nutrients (fibre, sugars, salt, saturated fat) are never given.
 */

/** What one shop's product card is good for. */
sealed interface ZakazMatch {
    val prefill: ProductPrefill

    /** A name and all four values, and they add up: log it straight away. */
    data class Usable(override val prefill: ProductPrefill) : ZakazMatch

    /**
     * Something, but not enough to log, or four values that don't add up:
     * the new-food form opens with every valid value filled in, and the user
     * checks them against the label.
     */
    data class Partial(override val prefill: ProductPrefill) : ZakazMatch
}

/**
 * The first number in a label value: a JSON number as it is, or the first
 * "12" / "12.5" / "12,5" inside a string ("9,95г" -> 9.95, "389,2/1625,6"
 * -> 389.2). Negative, non-finite or absent -> null.
 */
fun zakazNumber(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    val number = if (primitive.isString) {
        firstNumber.find(primitive.content.replace(',', '.'))?.value?.toDoubleOrNull()
    } else {
        // A JSON number; true/false/null have no numeric content.
        primitive.content.toDoubleOrNull()
    }
    return number?.takeIf { it.isFinite() && it >= 0.0 }
}

/**
 * Energy in kcal per 100 g. A value that names only kJ ("1625 кДж") is
 * converted; one that names kcal ("389 ккал / 1628 кДж") is kcal as written.
 * Above 900 kcal (pure fat is about 900) the value is nonsense -> null.
 */
fun zakazKcal(value: JsonElement?): Double? {
    val n = zakazNumber(value) ?: return null
    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
    val kcal = if (text != null && kjUnit.containsMatchIn(text) && !kcalUnit.containsMatchIn(text)) {
        // One decimal, rounded half up like the website's Math.round.
        (NutritionLabelMath.kcalFromKj(n) * 10).roundToLong() / 10.0
    } else {
        n
    }
    return kcal.takeIf { it in 0.0..900.0 }
}

/** Protein, fat or carbohydrates in g per 100 g; above 100 g -> null. */
fun zakazMacro(value: JsonElement?): Double? = zakazNumber(value)?.takeIf { it in 0.0..100.0 }

/**
 * Do the four values describe a real food? Energy must roughly match the
 * Atwater sum of the macros (shop catalogues sometimes carry another
 * product's numbers, or a kJ value labelled kcal), and the macros cannot
 * weigh more than the 100 g they are part of. The tolerance is wide on
 * purpose: fibre and polyols make labels drift from 4/9/4.
 *
 * Alcohol is the one big gap in 4/9/4 (about 7 kcal/g): a correct wine
 * label (about 80 kcal, 0/0/0.3) or vodka label (about 220 kcal, 0/0/0)
 * would always fail. So a drink whose title states its strength ([abv], see
 * [zakazAbv]) also passes when its energy matches the sum plus the alcohol:
 * 5.53 kcal per % per 100 ml (7 kcal/g at 0.789 g/ml). Only ever an extra
 * way to pass, never a reason to fail.
 */
fun zakazConsistent(kcal: Double, protein: Double, fat: Double, carbs: Double, abv: Double? = null): Boolean {
    if (protein + fat + carbs > 105.0) return false
    val expected = 4 * protein + 9 * fat + 4 * carbs
    fun matches(sum: Double) = abs(kcal - sum) <= 20 + 0.35 * max(kcal, sum)
    return matches(expected) || (abv != null && matches(expected + KCAL_PER_ABV_PERCENT * abv))
}

/**
 * The alcohol strength a drink's title states ("Вино ... 13% 0,75л" -> 13),
 * for [zakazConsistent]: the first "number %" in the title, at most 80.
 * null for food (a "70%" chocolate is not a drink) and for titles without
 * one; a "100%" juice says nothing about alcohol and is past the cap.
 */
fun zakazAbv(title: String?, isLiquid: Boolean): Double? {
    if (!isLiquid || title == null) return null
    val stated = abvInTitle.find(title)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    return stated?.takeIf { it <= 80.0 }
}

/**
 * One shop's product card -> what it is good for, or null when it holds no
 * name and no valid value at all. The body may be the product itself or
 * wrapped as {"product": {...}}. [scannedCode] is what the product gets
 * saved under, never a code from the card.
 */
fun mapZakazCard(body: JsonElement?, scannedCode: String): ZakazMatch? {
    val root = body as? JsonObject ?: return null
    val card = root["product"] as? JsonObject ?: root

    val name = ProductNames.clean(card.string("title"))
    val brand = (card["producer"] as? JsonObject)?.string("trademark")
        ?.trim { it.isWhitespace() || it == '\uFEFF' }
        // Unbranded goods (fresh produce, the shop's own counter) say so in words.
        ?.takeIf { it.isNotEmpty() && it.lowercase() !in noBrand }

    val facts = card["nutrition_facts"] as? JsonObject
    val kcal = zakazKcal(facts?.get("ingredient_energy"))
    val protein = zakazMacro(facts?.get("ingredient_protein"))
    val fat = zakazMacro(facts?.get("ingredient_fat"))
    val carbs = zakazMacro(facts?.get("ingredient_carbohydrates"))
    if (name == null && kcal == null && protein == null && fat == null && carbs == null) return null

    val isLiquid = zakazIsLiquid(card, name)
    val prefill = ProductPrefill(
        barcode = scannedCode,
        name = name,
        brand = brand,
        kcalPer100g = kcal,
        proteinPer100g = protein,
        fatPer100g = fat,
        carbsPer100g = carbs,
        // "weight" is the pack size, not a portion: no serving size.
        servingSizeG = null,
        isLiquid = isLiquid,
    )
    val usable = name != null && kcal != null && protein != null && fat != null && carbs != null &&
        zakazConsistent(kcal, protein, fat, carbs, zakazAbv(name, isLiquid))
    return if (usable) ZakazMatch.Usable(prefill) else ZakazMatch.Partial(prefill)
}

/** The cache row for a usable shop product: "zakaz:<scanned code>", read-only like OFF rows. */
fun ZakazMatch.Usable.toEntity(cachedAtEpochMillis: Long, isFavorite: Boolean): FoodProductEntity =
    FoodProductEntity(
        id = FoodProductEntity.zakazId(prefill.barcode),
        name = checkNotNull(prefill.name),
        brand = prefill.brand,
        kcalPer100g = checkNotNull(prefill.kcalPer100g),
        proteinPer100g = checkNotNull(prefill.proteinPer100g),
        fatPer100g = checkNotNull(prefill.fatPer100g),
        carbsPer100g = checkNotNull(prefill.carbsPer100g),
        servingSizeG = null,
        isLiquid = prefill.isLiquid,
        // Detail nutrients stay null ("not stated"): the shops never give them.
        source = ProductSource.ZAKAZ,
        cachedAtEpochMillis = cachedAtEpochMillis,
        isFavorite = isFavorite,
    )

/**
 * The retail chains worth asking, best first: the biggest catalogues with
 * the most filled-in nutrition. Matched case-insensitively against a store's
 * retail_chain; every other chain is ignored.
 */
val ZAKAZ_CHAINS = listOf(
    "auchan", "novus", "metro", "megamarket", "ekomarket", "tavriav",
    "ultramarket", "epicentr", "vostorg", "chudomarket", "zaraz",
)

/**
 * Used whenever the store list can't be fetched or holds nothing usable:
 * one store each of Auchan, Novus, METRO, EKO Market and UltraMarket.
 */
val ZAKAZ_FALLBACK_STORES = listOf("48246401", "48201031", "48215611", "48280214", "48277601")

/**
 * The most stores one scan asks, all at once. NetworkModule lets the shops'
 * HTTP client run this many requests to the one host in parallel (OkHttp's
 * default is five, which queued the sixth store behind the others).
 */
const val ZAKAZ_MAX_STORES = 6

/**
 * The stores to ask, from the API's store list: one active store per chain
 * in [ZAKAZ_CHAINS] order, at most [ZAKAZ_MAX_STORES]. Within a chain a Kyiv store wins
 * (the capital's stores carry the widest range), else the first active one
 * in list order. Empty when [stores] is not a list or holds nothing usable;
 * the caller then uses [ZAKAZ_FALLBACK_STORES].
 */
fun pickZakazStores(stores: JsonElement?): List<String> {
    val active = (stores as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .filter { store ->
            val isActive = store["is_active"] as? JsonPrimitive
            isActive != null && !isActive.isString && isActive.content == "true" &&
                !store.string("id").isNullOrEmpty()
        }
    return ZAKAZ_CHAINS.mapNotNull { chain ->
        val inChain = active.filter { it.string("retail_chain").equals(chain, ignoreCase = true) }
        val pick = inChain.firstOrNull { store -> store.string("city")?.let { kyiv.containsMatchIn(it) } == true }
            ?: inChain.firstOrNull()
        pick?.string("id")
    }.take(ZAKAZ_MAX_STORES)
}

/**
 * A drink: the card states a volume ("volume": 500 for 0,5 l); else goods
 * sold by weight are food; else the pack size in the title decides ("0,5л",
 * "900мл"), by the same rule as for Open Food Facts.
 */
private fun zakazIsLiquid(card: JsonObject, name: String?): Boolean {
    val volume = card["volume"] as? JsonPrimitive
    if (volume != null && !volume.isString && (volume.content.toDoubleOrNull() ?: 0.0) > 0.0) return true
    if (card.string("unit") == "kg") return false
    return LiquidDetector.isLiquid(nutritionDataPer = null, quantity = name, servingSize = null)
}

/** A string field's value; null when absent or not a JSON string. */
private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private val firstNumber = Regex("""-?\d+(?:\.\d+)?""")
private val abvInTitle = Regex("""(\d+(?:[.,]\d+)?)\s*%""")

/** Energy of alcohol per 1 % ABV in 100 ml: 0.789 g of ethanol at about 7 kcal/g. */
private const val KCAL_PER_ABV_PERCENT = 5.53
private val kjUnit = Regex("kj|кдж", RegexOption.IGNORE_CASE)
private val kcalUnit = Regex("kcal|ккал", RegexOption.IGNORE_CASE)
private val kyiv = Regex("ки[їє]в|kyiv|kiev", RegexOption.IGNORE_CASE)
private val noBrand = setOf("без тм", "без торгової марки")
