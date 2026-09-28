package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.ProductSource

/**
 * Offline cache of food products. Every product the user ever looked up is
 * stored here, so search keeps working without internet.
 *
 * id format: "off:<barcode>" for Open Food Facts products,
 *            "local:<uuid>" for user-created foods (future).
 *
 * The four per-100g values are NON-null on purpose: the API mapper simply
 * drops incomplete products, so everything in this table is fully usable.
 *
 * Writes go through @Upsert ONLY. Never use OnConflict.REPLACE here: this
 * table is a foreign-key parent of food_log_entry, and REPLACE works as
 * delete+insert, which would fire SET_NULL on every diary row.
 */
@Entity(tableName = "food_product")
data class FoodProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
    /** Label portion size in grams, when the producer stated one. */
    val servingSizeG: Double?,
    /**
     * A drink (v15): amounts are typed and shown in ml. The per-100 values are
     * unchanged (1 ml of a drink is about 1 g), only the unit label differs.
     */
    val isLiquid: Boolean = false,
    // Detail nutrients (v0.11): null = the source didn't state them — never 0.
    val fiberPer100g: Double? = null,
    val sugarsPer100g: Double? = null,
    val saltPer100g: Double? = null,
    val saturatedFatPer100g: Double? = null,
    /** E-codes as CSV ("E330,E202"), null = none known. */
    val additivesCsv: String? = null,
    val source: ProductSource,
    val cachedAtEpochMillis: Long,
    /**
     * Starred by the user. CAREFUL: refreshing a product from the API must
     * carry this flag over (see FoodRepository.search) — a plain upsert of a
     * fresh DTO would silently wipe the star.
     */
    val isFavorite: Boolean = false,
)
