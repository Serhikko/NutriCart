package com.nutricart.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// A SECOND DataStore file, separate from "settings" on purpose: only this one
// is excluded from Android's cloud backup and device transfer (see
// res/xml/backup_rules.xml). Keeping the key in the shared file would have
// meant excluding the onboarding flag and the reminder times from backup too.
private val Context.secretsDataStore by preferencesDataStore(name = "secrets")

/**
 * The user's own secrets: the API key for the optional AI assistant and the
 * Telegram bot token for the optional partner feature.
 *
 * Stored in plain text and that is a deliberate, written-down decision:
 * androidx.security is deprecated and not a dependency here, and for a
 * single-user personal app the lock screen is the real boundary. The screen
 * says so next to the field, and there is a Delete button.
 */
@Singleton
class SecretsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
    }

    /** null = never set. */
    val aiApiKey: Flow<String?> =
        context.secretsDataStore.data.map { prefs -> prefs[Keys.AI_API_KEY] }

    suspend fun setAiApiKey(value: String) {
        context.secretsDataStore.edit { prefs -> prefs[Keys.AI_API_KEY] = value }
    }

    /** remove(), not "": a blank value would be indistinguishable from unset. */
    suspend fun clearAiApiKey() {
        context.secretsDataStore.edit { prefs -> prefs.remove(Keys.AI_API_KEY) }
    }

    /** The partner feature's bot token (created by the user in @BotFather). null = never set. */
    val telegramBotToken: Flow<String?> =
        context.secretsDataStore.data.map { prefs -> prefs[Keys.TELEGRAM_BOT_TOKEN] }

    suspend fun setTelegramBotToken(value: String) {
        context.secretsDataStore.edit { prefs -> prefs[Keys.TELEGRAM_BOT_TOKEN] = value }
    }

    suspend fun clearTelegramBotToken() {
        context.secretsDataStore.edit { prefs -> prefs.remove(Keys.TELEGRAM_BOT_TOKEN) }
    }

    /**
     * Part of "reset the app". The second store is exactly the kind of thing
     * that gets forgotten in a reset — the same class of bug that shipped in
     * v0.2-v0.8 with per-DAO deletes — so it is wired into ProfileRepository's
     * one reset path and nowhere else.
     */
    suspend fun resetAll() {
        context.secretsDataStore.edit { prefs -> prefs.clear() }
    }
}
