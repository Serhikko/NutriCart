package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.WeightSource

/**
 * One weight measurement. The unique index allows at most one entry per day
 * per source (a manual one and a watch one can coexist on the same day;
 * queries in WeightDao prefer MANUAL).
 */
@Entity(
    tableName = "weight_entry",
    indices = [Index(value = ["epochDay", "source"], unique = true)],
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Days since 1970-01-01 (LocalDate.toEpochDay()) — easy to sort and compare.
    val epochDay: Long,
    val weightKg: Double,
    val source: WeightSource,
)
