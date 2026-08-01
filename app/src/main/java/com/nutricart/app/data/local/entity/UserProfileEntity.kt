package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex

/**
 * Single-row table: there is exactly one user, so id is always [SINGLETON_ID].
 * The current weight is NOT stored here — onboarding writes it as the first
 * row of weight_entry, and later screens always read the latest weight_entry.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val sex: Sex,
    // Birth date instead of age: age is derived from it, so it never goes stale.
    val birthDateEpochDay: Long,
    val heightCm: Int,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val targetKgPerWeek: Double,
    // How many snacks the meal plan should include per day (0..2), chosen in onboarding.
    val snacksPerDay: Int,
    val isVegetarian: Boolean,
    val noPork: Boolean,
    // Stored as CSV of enum names via Converters.
    val allergies: List<Allergen>,
    val createdAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
