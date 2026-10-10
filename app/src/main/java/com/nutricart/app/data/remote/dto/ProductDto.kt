package com.nutricart.app.data.remote.dto

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.LiquidDetector
import com.nutricart.app.domain.logic.NutritionLabelMath
import com.nutricart.app.domain.logic.ProductNames
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ProductSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

@Serializable
data class SearchResponseDto(
    val products: List<ProductDto> = emptyList(),
)

/**
 * The Open Food Facts fields name search reads: [OFF_PRODUCT_FIELDS] minus
 * nutriments_estimated, which only the scanner's prefill uses — a page of
 * 25 products would carry dozens of estimated values each for nothing.
 */
const val OFF_SEARCH_FIELDS =
    "code,product_name,product_name_en,product_name_uk,product_name_ru,product_name_be," +
        "generic_name,generic_name_en,generic_name_uk,generic_name_ru,generic_name_be," +
        "brands,nutriments,serving_quantity,serving_size,quantity,nutrition_data_per,additives_tags"

/**
 * Every Open Food Facts field the app reads. OFF returns ONLY the requested
 * fields — a field missing here is silently absent from every response
 * (review-caught: the additives feature shipped dead because additives_tags
 * wasn't listed). ProductDtoMappingTest checks that every field ProductDto
 * declares is in this list. nutriments covers every _100g and _serving
 * column at once; nutriments_estimated is OFF's own estimate from the
 * ingredients (see [toPrefill]).
 */
const val OFF_PRODUCT_FIELDS = "$OFF_SEARCH_FIELDS,nutriments_estimated"

@Serializable
data class ProductResponseDto(
    /** 1 = found, 0 = unknown code. null when the field is absent. */
    val status: Int? = null,
    val product: ProductDto? = null,
)

@Serializable
data class ProductDto(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    /**
     * The English name. Usually a copy of product_name — but for a product
     * whose main language is something else, product_name can be blank while
     * the English name is filled in (common for UK-sold imports), and those
     * used to be dropped as "nameless".
     */
    @SerialName("product_name_en") val productNameEn: String? = null,
    /**
     * Ukrainian / Russian / Belarusian names. Products from Ukraine (GS1 482)
     * and Belarus (481) often have ONLY these, with product_name blank; see
     * ProductNames for the order they are tried in.
     */
    @SerialName("product_name_uk") val productNameUk: String? = null,
    @SerialName("product_name_ru") val productNameRu: String? = null,
    @SerialName("product_name_be") val productNameBe: String? = null,
    /** "Kefir 2.5%": the generic description, the last resort for a name. */
    @SerialName("generic_name") val genericName: String? = null,
    @SerialName("generic_name_en") val genericNameEn: String? = null,
    @SerialName("generic_name_uk") val genericNameUk: String? = null,
    @SerialName("generic_name_ru") val genericNameRu: String? = null,
    @SerialName("generic_name_be") val genericNameBe: String? = null,
    val brands: String? = null,
    val nutriments: NutrimentsDto? = null,
    /**
     * OFF's estimate from the ingredient list, for products nobody typed
     * the label of. Only ever a starting point for the form the user
     * confirms ([toPrefill]), never the values of a product logged as is.
     * Read leniently ([LenientNutrimentsSerializer]): an odd value here must
     * not cost the product itself.
     */
    @Serializable(with = LenientNutrimentsSerializer::class)
    @SerialName("nutriments_estimated") val nutrimentsEstimated: NutrimentsDto? = null,
    // String on purpose: this community-filled field can arrive as a number,
    // a quoted number, an empty string or even "2 pcs". Declaring it Double
    // would make ONE bad product break decoding of the whole search response.
    // (With isLenient, bare JSON numbers also decode into String just fine.)
    @SerialName("serving_quantity") val servingQuantity: String? = null,
    /** "250 ml", "1 can (330 ml)": the text form, read only to tell drinks from food. */
    @SerialName("serving_size") val servingSize: String? = null,
    /** Pack size as printed, e.g. "500 ml" or "400 g". */
    val quantity: String? = null,
    /** "100g" or "100ml": what the nutriment values refer to. */
    @SerialName("nutrition_data_per") val nutritionDataPer: String? = null,
    /** OFF additive tags, e.g. ["en:e330", "en:e202"]. */
    @SerialName("additives_tags") val additivesTags: List<String>? = null,
)

/**
 * Every nutriment goes through [LenientDoubleSerializer]: a malformed value
 * becomes null instead of failing the whole response.
 *
 * Besides the per-100g values, the per-SERVING columns are mapped too. Many
 * UK packs print nutrition per portion only, and contributors enter exactly
 * what the pack says — those products have `_serving` values, a serving size,
 * and no `_100g` at all. The mapper rescales them.
 */
@Serializable
data class NutrimentsDto(
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy-kcal_100g") val kcalPer100g: Double? = null,
    /** Energy in kJ — some entries state only this (labels always print kJ). */
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy-kj_100g") val kjPer100g: Double? = null,
    /** OFF's generic energy field; stored in kJ regardless of the label unit. */
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy_100g") val energyPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("proteins_100g") val proteinPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fat_100g") val fatPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("carbohydrates_100g") val carbsPer100g: Double? = null,
    // Optional detail nutrients: missing values do NOT drop the product —
    // they stay null ("OFF doesn't know"), unlike the four core values above.
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fiber_100g") val fiberPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("sugars_100g") val sugarsPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("salt_100g") val saltPer100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("saturated-fat_100g") val saturatedFatPer100g: Double? = null,

    // Per-serving columns, used only when the per-100g ones are missing.
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy-kcal_serving") val kcalPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy-kj_serving") val kjPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy_serving") val energyPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("proteins_serving") val proteinPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fat_serving") val fatPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("carbohydrates_serving") val carbsPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fiber_serving") val fiberPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("sugars_serving") val sugarsPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("salt_serving") val saltPerServing: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("saturated-fat_serving") val saturatedFatPerServing: Double? = null,
)

/**
 * [NutrimentsDto] where anything but a JSON object ("n/a", [], a number)
 * reads as null. Without it ONE such value failed decoding of the whole
 * product, and the scan reported Open Food Facts as unavailable even for a
 * complete product (review-caught; the website ignores such a value too).
 * The values inside stay lenient through [LenientDoubleSerializer].
 */
internal object LenientNutrimentsSerializer : KSerializer<NutrimentsDto?> {

    override val descriptor: SerialDescriptor = NutrimentsDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): NutrimentsDto? {
        val json = decoder as? JsonDecoder ?: return NutrimentsDto.serializer().deserialize(decoder)
        val element = json.decodeJsonElement() as? JsonObject ?: return null
        // The caller's Json, so its settings (ignoreUnknownKeys...) still apply.
        return json.json.decodeFromJsonElement(NutrimentsDto.serializer(), element)
    }

    override fun serialize(encoder: Encoder, value: NutrimentsDto?) {
        if (value == null) encoder.encodeNull() else NutrimentsDto.serializer().serialize(encoder, value)
    }
}

/**
 * The product's name in the language order of its barcode's origin (see
 * ProductNames); [code] decides that origin. null = no name anywhere.
 */
fun ProductDto.resolvedName(code: String?): String? = ProductNames.resolve(
    code = code,
    productName = productName,
    productNameIn = { lang ->
        when (lang) {
            "en" -> productNameEn
            "uk" -> productNameUk
            "ru" -> productNameRu
            "be" -> productNameBe
            else -> null
        }
    },
    genericName = genericName,
    genericNameIn = { lang ->
        when (lang) {
            "en" -> genericNameEn
            "uk" -> genericNameUk
            "ru" -> genericNameRu
            "be" -> genericNameBe
            else -> null
        }
    },
)

/**
 * What the scanner's "incomplete product" form starts with: everything
 * [resolved] knows, and for each value still unknown OFF's estimate from the
 * ingredients (nutriments_estimated), per 100 g. The user checks every
 * number against the label before saving, so an estimate is a fair start
 * here; it never makes a product "found" ([toEntityOrNull] ignores it). A
 * product with a name and only estimated values therefore opens the form,
 * prefilled, and [ProductPrefill.estimated] tells the form to ask for a
 * check of those numbers rather than for missing ones.
 *
 * [lookedUpCode] is the barcode for a product whose own code is missing;
 * null only when neither is known.
 */
fun ProductDto.toPrefill(lookedUpCode: String? = null): ProductPrefill? {
    val p = resolved(lookedUpCode) ?: return null
    val e = nutrimentsEstimated ?: return p
    return p.copy(
        estimated = (p.kcalPer100g == null && e.kcalPer100g != null) ||
            (p.proteinPer100g == null && e.proteinPer100g != null) ||
            (p.fatPer100g == null && e.fatPer100g != null) ||
            (p.carbsPer100g == null && e.carbsPer100g != null),
        kcalPer100g = p.kcalPer100g ?: e.kcalPer100g,
        proteinPer100g = p.proteinPer100g ?: e.proteinPer100g,
        fatPer100g = p.fatPer100g ?: e.fatPer100g,
        carbsPer100g = p.carbsPer100g ?: e.carbsPer100g,
        fiberPer100g = p.fiberPer100g ?: e.fiberPer100g,
        sugarsPer100g = p.sugarsPer100g ?: e.sugarsPer100g,
        saltPer100g = p.saltPer100g ?: e.saltPer100g,
        saturatedFatPer100g = p.saturatedFatPer100g ?: e.saturatedFatPer100g,
    )
}

/**
 * Everything OFF's contributors stated about this product, each value
 * RESOLVED — the one path both [toEntityOrNull] and [toPrefill] use, so the
 * two can never disagree about a stated number:
 *
 *  - energy: kcal/100 g, else kJ/100 g converted, else a per-serving value
 *    rescaled by the serving size, else the Atwater sum of the macros;
 *  - protein / fat / carbs and the detail nutrients: per 100 g, else per
 *    serving rescaled.
 *
 * Unknown values stay null; estimates are not used here.
 */
private fun ProductDto.resolved(lookedUpCode: String?): ProductPrefill? {
    val barcode = code?.takeIf { it.isNotBlank() }
        ?: lookedUpCode?.takeIf { it.isNotBlank() }
        ?: return null
    val n = nutriments

    // Garbage portion sizes ("", "2 pcs") just become null — the product
    // itself stays usable, portion mode is simply unavailable for it.
    val servingSizeG = servingQuantity?.let { LenientDoubleSerializer.parse(it) }?.takeIf { it > 0.0 }

    // Per 100 g first; a per-serving value rescaled is the fallback.
    fun resolve(per100g: Double?, perServing: Double?): Double? =
        per100g ?: NutritionLabelMath.per100gFromServing(perServing, servingSizeG)

    val protein = resolve(n?.proteinPer100g, n?.proteinPerServing)
    val fat = resolve(n?.fatPer100g, n?.fatPerServing)
    val carbs = resolve(n?.carbsPer100g, n?.carbsPerServing)
    val kcal = NutritionLabelMath.resolveKcalPer100g(
        kcalPer100g = n?.kcalPer100g,
        kjPer100g = n?.kjPer100g ?: n?.energyPer100g,
        kcalPerServing = n?.kcalPerServing,
        kjPerServing = n?.kjPerServing ?: n?.energyPerServing,
        servingSizeG = servingSizeG,
        proteinPer100g = protein,
        fatPer100g = fat,
        carbsPer100g = carbs,
    )

    return ProductPrefill(
        barcode = barcode,
        name = resolvedName(barcode),
        // OFF lists brands as a comma-separated string; the first one is enough.
        brand = brands?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        kcalPer100g = kcal,
        proteinPer100g = protein,
        fatPer100g = fat,
        carbsPer100g = carbs,
        servingSizeG = servingSizeG,
        isLiquid = LiquidDetector.isLiquid(nutritionDataPer, quantity, servingSize),
        fiberPer100g = resolve(n?.fiberPer100g, n?.fiberPerServing),
        sugarsPer100g = resolve(n?.sugarsPer100g, n?.sugarsPerServing),
        saltPer100g = resolve(n?.saltPer100g, n?.saltPerServing),
        saturatedFatPer100g = resolve(n?.saturatedFatPer100g, n?.saturatedFatPerServing),
    )
}

/**
 * DTO -> cache entity. Open Food Facts data is community-filled and often
 * incomplete. A product still needs a barcode, a name and the four core
 * values, each resolved by [resolved] (per serving rescaled, kJ converted,
 * energy from the macros...). Only when a value is still unknown after that
 * is the product dropped (returns null) — the scanner then offers the
 * prefilled form instead. Before, a UK pack entered per portion, or with kJ
 * only, was silently invisible to search and the scanner. OFF's estimates
 * are never used here: a product logged without the user's check must carry
 * stated values only.
 */
fun ProductDto.toEntityOrNull(cachedAtEpochMillis: Long): FoodProductEntity? {
    val barcode = code?.takeIf { it.isNotBlank() } ?: return null
    val p = resolved(barcode) ?: return null
    return FoodProductEntity(
        id = "off:$barcode",
        name = p.name ?: return null,
        brand = p.brand,
        kcalPer100g = p.kcalPer100g ?: return null,
        proteinPer100g = p.proteinPer100g ?: return null,
        fatPer100g = p.fatPer100g ?: return null,
        carbsPer100g = p.carbsPer100g ?: return null,
        servingSizeG = p.servingSizeG,
        isLiquid = p.isLiquid,
        fiberPer100g = p.fiberPer100g,
        sugarsPer100g = p.sugarsPer100g,
        saltPer100g = p.saltPer100g,
        saturatedFatPer100g = p.saturatedFatPer100g,
        // "en:e330" -> "E330". [A-Z]* (not ?): OFF subtypes can be roman
        // numerals, e.g. "en:e500ii" -> E500II — one letter would drop them.
        additivesCsv = additivesTags
            ?.mapNotNull { tag ->
                tag.substringAfter(':').uppercase().takeIf { it.matches(Regex("E\\d+[A-Z]*")) }
            }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(","),
        source = ProductSource.OPEN_FOOD_FACTS,
        cachedAtEpochMillis = cachedAtEpochMillis,
    )
}
