package com.nutricart.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutricart.app.domain.model.MealSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// One DataStore file named "settings" for the whole app.
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Tiny key-value storage for flags and settings.
 * Health data lives in Room; DataStore only keeps small app state like
 * "has the user finished onboarding".
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_HC_SYNC_EPOCH_MILLIS = longPreferencesKey("last_hc_sync_epoch_millis")
        val SHOPPING_SELECTED_DAYS = stringPreferencesKey("shopping_selected_days_csv")
        val LAST_RECURRING_DAY = longPreferencesKey("last_recurring_materialized_day")
    }

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = value }
    }

    /** null = never synced with Health Connect yet. */
    val lastHcSyncEpochMillis: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.LAST_HC_SYNC_EPOCH_MILLIS] }

    suspend fun setLastHcSyncEpochMillis(value: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_HC_SYNC_EPOCH_MILLIS] = value }
    }

    /** Which plan days the last shopping list was built from (epoch days as CSV). */
    val shoppingSelectedDays: Flow<Set<Long>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.SHOPPING_SELECTED_DAYS]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun setShoppingSelectedDays(days: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOPPING_SELECTED_DAYS] = days.joinToString(",")
        }
    }

    /** Sensible default reminder times, minutes from midnight. */
    private fun defaultReminderMinutes(slot: MealSlot): Int = when (slot) {
        MealSlot.BREAKFAST -> 8 * 60
        MealSlot.LUNCH -> 13 * 60
        MealSlot.DINNER -> 19 * 60
        MealSlot.SNACK -> 16 * 60
    }

    private fun reminderEnabledKey(slot: MealSlot) =
        booleanPreferencesKey("reminder_${slot.name}_enabled")

    private fun reminderMinutesKey(slot: MealSlot) =
        intPreferencesKey("reminder_${slot.name}_minutes")

    private fun reminderLastNotifiedKey(slot: MealSlot) =
        longPreferencesKey("reminder_${slot.name}_last_notified_day")

    /** The last day this slot's reminder actually fired — the once-per-day
     *  guard against clock shifts double-firing (DST, travel, corrections). */
    fun reminderLastNotifiedDay(slot: MealSlot): Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[reminderLastNotifiedKey(slot)] }

    suspend fun setReminderLastNotifiedDay(slot: MealSlot, epochDay: Long) {
        context.dataStore.edit { prefs -> prefs[reminderLastNotifiedKey(slot)] = epochDay }
    }

    fun reminderEnabled(slot: MealSlot): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[reminderEnabledKey(slot)] ?: false }

    fun reminderMinutes(slot: MealSlot): Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[reminderMinutesKey(slot)] ?: defaultReminderMinutes(slot)
        }

    suspend fun setReminderEnabled(slot: MealSlot, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[reminderEnabledKey(slot)] = enabled }
    }

    suspend fun setReminderMinutes(slot: MealSlot, minutesOfDay: Int) {
        context.dataStore.edit { prefs -> prefs[reminderMinutesKey(slot)] = minutesOfDay }
    }

    /**
     * The last day recurring workouts were materialized (null = never).
     * Once-per-day: deleting an auto-added workout must NOT resurrect it on
     * the next app open.
     */
    val lastRecurringMaterializedDay: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.LAST_RECURRING_DAY] }

    suspend fun setLastRecurringMaterializedDay(epochDay: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_RECURRING_DAY] = epochDay }
    }

    /**
     * Wipes EVERY stored key (used only by the app reset). Clearing beats
     * resetting keys one by one: a key added later can never be forgotten here.
     */
    suspend fun resetAll() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
