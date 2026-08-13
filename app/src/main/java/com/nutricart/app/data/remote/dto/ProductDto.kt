package com.nutricart.app.data.remote.dto

import com.nutricart.app.data.local.entity.FoodProductEntity
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

@Serializable
data class NutrimentsDto(
    @SerialName("energy-kcal_100g") val kcalPer100g: Double? = null,
    @SerialName("proteins_100g") val proteinPer100g: Double? = null,
    @SerialName("fat_100g") val fatPer100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbsPer100g: Double? = null,
    // Optional detail nutrients: missing values do NOT drop the product —
    // they stay null ("OFF doesn't know"), unlike the four core values above.
    @SerialName("fiber_100g") val fiberPer100g: Double? = null,
    @SerialName("sugars_100g") val sugarsPer100g: Double? = null,
    @SerialName("salt_100g") val saltPer100g: Double? = null,
    @SerialName("saturated-fat_100g") val saturatedFatPer100g: Double? = null,
)

/**
 * DTO -> cache entity. Open Food Facts data is community-filled and often
 * incomplete; a product missing its name, barcode or ANY of the four per-100g
 * values is dropped (returns null) so only fully usable products reach the
 * search results and the offline cache.
 */
fun ProductDto.toEntityOrNull(cachedAtEpochMillis: Long): FoodProductEntity? {
    val barcode = code?.takeIf { it.isNotBlank() } ?: return null
    val name = productName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val n = nutriments ?: return null
    return FoodProductEntity(
        id = "off:$barcode",
        name = name,
        // OFF lists brands as a comma-separated string; the first one is enough.
        brand = brands?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        kcalPer100g = n.kcalPer100g ?: return null,
        proteinPer100g = n.proteinPer100g ?: return null,
        fatPer100g = n.fatPer100g ?: return null,
        carbsPer100g = n.carbsPer100g ?: return null,
        // Garbage portion sizes ("", "2 pcs") just become null — the product
        // itself stays usable, portion mode is simply unavailable for it.
        servingSizeG = servingQuantity?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 },
        fiberPer100g = n.fiberPer100g,
        sugarsPer100g = n.sugarsPer100g,
        saltPer100g = n.saltPer100g,
        saturatedFatPer100g = n.saturatedFatPer100g,
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
