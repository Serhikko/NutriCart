package com.nutricart.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = value }
    }
}
