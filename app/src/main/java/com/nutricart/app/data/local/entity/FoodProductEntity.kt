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
    val source: ProductSource,
    val cachedAtEpochMillis: Long,
)
