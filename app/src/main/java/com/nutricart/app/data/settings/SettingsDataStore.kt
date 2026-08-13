package com.nutricart.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    /**
     * Wipes EVERY stored key (used only by the app reset). Clearing beats
     * resetting keys one by one: a key added later can never be forgotten here.
     */
    suspend fun resetAll() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
