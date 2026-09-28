package com.nutricart.app.data.remote.dto

import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.NutritionLabelMath
import com.nutricart.app.domain.model.ProductSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val products: List<ProductDto> = emptyList(),
)

@Serializable
data class ProductResponseDto(
    val status: Int = 0,
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
    val brands: String? = null,
    val nutriments: NutrimentsDto? = null,
    // String on purpose: this community-filled field can arrive as a number,
    // a quoted number, an empty string or even "2 pcs". Declaring it Double
    // would make ONE bad product break decoding of the whole search response.
    // (With isLenient, bare JSON numbers also decode into String just fine.)
    @SerialName("serving_quantity") val servingQuantity: String? = null,
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
 * DTO -> cache entity. Open Food Facts data is community-filled and often
 * incomplete. A product still needs a barcode, a name and the four core
 * values — but each core value is now RESOLVED, not just read:
 *
 *  - energy: kcal/100 g, else kJ/100 g converted, else a per-serving value
 *    rescaled by the serving size, else the Atwater sum of the macros;
 *  - protein / fat / carbs: per 100 g, else per serving rescaled.
 *
 * Only when a value is still unknown after that is the product dropped
 * (returns null). Before, a UK pack entered per portion, or with kJ only,
 * was silently invisible to search and the scanner.
 */
fun ProductDto.toEntityOrNull(cachedAtEpochMillis: Long): FoodProductEntity? {
    val barcode = code?.takeIf { it.isNotBlank() } ?: return null
    val name = productName?.trim()?.takeIf { it.isNotBlank() }
        ?: productNameEn?.trim()?.takeIf { it.isNotBlank() }
        ?: return null
    val n = nutriments ?: return null

    // Garbage portion sizes ("", "2 pcs") just become null — the product
    // itself stays usable, portion mode is simply unavailable for it.
    val servingSizeG = servingQuantity?.let { LenientDoubleSerializer.parse(it) }?.takeIf { it > 0.0 }

    // Per 100 g first; a per-serving value rescaled is the fallback.
    fun core(per100g: Double?, perServing: Double?): Double? =
        per100g ?: NutritionLabelMath.per100gFromServing(perServing, servingSizeG)

    val protein = core(n.proteinPer100g, n.proteinPerServing)
    val fat = core(n.fatPer100g, n.fatPerServing)
    val carbs = core(n.carbsPer100g, n.carbsPerServing)
    val kcal = NutritionLabelMath.resolveKcalPer100g(
        kcalPer100g = n.kcalPer100g,
        kjPer100g = n.kjPer100g ?: n.energyPer100g,
        kcalPerServing = n.kcalPerServing,
        kjPerServing = n.kjPerServing ?: n.energyPerServing,
        servingSizeG = servingSizeG,
        proteinPer100g = protein,
        fatPer100g = fat,
        carbsPer100g = carbs,
    )

    return FoodProductEntity(
        id = "off:$barcode",
        name = name,
        // OFF lists brands as a comma-separated string; the first one is enough.
        brand = brands?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        kcalPer100g = kcal ?: return null,
        proteinPer100g = protein ?: return null,
        fatPer100g = fat ?: return null,
        carbsPer100g = carbs ?: return null,
        servingSizeG = servingSizeG,
        fiberPer100g = core(n.fiberPer100g, n.fiberPerServing),
        sugarsPer100g = core(n.sugarsPer100g, n.sugarsPerServing),
        saltPer100g = core(n.saltPer100g, n.saltPerServing),
        saturatedFatPer100g = core(n.saturatedFatPer100g, n.saturatedFatPerServing),
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
