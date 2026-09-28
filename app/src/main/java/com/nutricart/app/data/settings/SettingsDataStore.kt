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
 * Who receives the partner updates: one Telegram chat, one person in it.
 * [userId] matters in a group chat — only the partner's messages become
 * notifications, never the user's own.
 */
data class PartnerLink(
    val chatId: Long,
    val userId: Long,
    val name: String,
)

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

        // Partner sharing (Telegram). The bot token itself is a secret and
        // lives in SecretsDataStore; everything here may be backed up.
        val PARTNER_CHAT_ID = longPreferencesKey("partner_chat_id")
        val PARTNER_USER_ID = longPreferencesKey("partner_user_id")
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val PARTNER_BOT_USERNAME = stringPreferencesKey("partner_bot_username")
        val PARTNER_SHARE_MEALS = booleanPreferencesKey("partner_share_meals")
        val PARTNER_NOTIFY_MISSED = booleanPreferencesKey("partner_notify_missed")
        val PARTNER_INBOX_ENABLED = booleanPreferencesKey("partner_inbox_enabled")
        val PARTNER_UPDATE_OFFSET = longPreferencesKey("partner_update_offset")

        // Cloud sync (Supabase). The session tokens live in SecretsDataStore.
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val CLOUD_DISPLAY_NAME = stringPreferencesKey("cloud_display_name")
        val CLOUD_DEVICE_ID = stringPreferencesKey("cloud_device_id")
        val CLOUD_LAST_SYNC = longPreferencesKey("cloud_last_sync_epoch_millis")
        val CLOUD_LAST_ERROR = stringPreferencesKey("cloud_last_error")
        val CLOUD_PAIRING_CODE = stringPreferencesKey("cloud_pairing_code")
        val CLOUD_PAIRING_EXPIRES_AT = longPreferencesKey("cloud_pairing_expires_at_epoch_millis")
        /** The email linked to the cloud account (milestone 3); null = anonymous. */
        val CLOUD_EMAIL = stringPreferencesKey("cloud_email")
        /** Pull watermarks are keyed per table: "cloud_pull_<table>". */
        fun cloudPullKey(table: String) = stringPreferencesKey("cloud_pull_$table")
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

    // --- Partner sharing ---

    /** null = no partner connected yet. */
    val partnerLink: Flow<PartnerLink?> =
        context.dataStore.data.map { prefs ->
            val chatId = prefs[Keys.PARTNER_CHAT_ID]
            val userId = prefs[Keys.PARTNER_USER_ID]
            if (chatId == null || userId == null) null
            else PartnerLink(chatId, userId, prefs[Keys.PARTNER_NAME].orEmpty())
        }

    /** null clears the link (the toggles and the bot stay). */
    suspend fun setPartnerLink(link: PartnerLink?) {
        context.dataStore.edit { prefs ->
            if (link == null) {
                prefs.remove(Keys.PARTNER_CHAT_ID)
                prefs.remove(Keys.PARTNER_USER_ID)
                prefs.remove(Keys.PARTNER_NAME)
            } else {
                prefs[Keys.PARTNER_CHAT_ID] = link.chatId
                prefs[Keys.PARTNER_USER_ID] = link.userId
                prefs[Keys.PARTNER_NAME] = link.name
            }
        }
    }

    /** The bot's @username, for the "open t.me/…" instruction. null = token not verified yet. */
    val partnerBotUsername: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.PARTNER_BOT_USERNAME] }

    suspend fun setPartnerBotUsername(username: String?) {
        context.dataStore.edit { prefs ->
            if (username == null) prefs.remove(Keys.PARTNER_BOT_USERNAME)
            else prefs[Keys.PARTNER_BOT_USERNAME] = username
        }
    }

    // The three toggles default to ON: connecting a partner means wanting
    // all of it, and each can be switched off individually afterwards.
    val partnerShareMeals: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.PARTNER_SHARE_MEALS] ?: true }

    suspend fun setPartnerShareMeals(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PARTNER_SHARE_MEALS] = enabled }
    }

    val partnerNotifyMissed: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.PARTNER_NOTIFY_MISSED] ?: true }

    suspend fun setPartnerNotifyMissed(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PARTNER_NOTIFY_MISSED] = enabled }
    }

    val partnerInboxEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.PARTNER_INBOX_ENABLED] ?: true }

    suspend fun setPartnerInboxEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PARTNER_INBOX_ENABLED] = enabled }
    }

    /** getUpdates cursor: the next update_id to ask for. null = start from the queue's tail. */
    val partnerUpdateOffset: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.PARTNER_UPDATE_OFFSET] }

    suspend fun setPartnerUpdateOffset(offset: Long?) {
        context.dataStore.edit { prefs ->
            if (offset == null) prefs.remove(Keys.PARTNER_UPDATE_OFFSET)
            else prefs[Keys.PARTNER_UPDATE_OFFSET] = offset
        }
    }

    // --- Cloud sync ---

    val cloudSyncEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_SYNC_ENABLED] ?: false }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CLOUD_SYNC_ENABLED] = enabled }
    }

    /** How the website names this account ("Serhii's day"). null = not set yet. */
    val cloudDisplayName: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_DISPLAY_NAME] }

    suspend fun setCloudDisplayName(name: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CLOUD_DISPLAY_NAME] = name }
    }

    /**
     * A random id minted once per install; it prefixes every row id this phone
     * sends, so two phones on one account (a later milestone) can never
     * collide. null = never minted.
     */
    val cloudDeviceId: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_DEVICE_ID] }

    suspend fun setCloudDeviceId(id: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CLOUD_DEVICE_ID] = id }
    }

    val cloudLastSyncEpochMillis: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_LAST_SYNC] }

    /** A short error code from the last failed sync ("offline", "auth", ...); null = fine. */
    val cloudLastError: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_LAST_ERROR] }

    suspend fun recordCloudSync(nowEpochMillis: Long?, error: String?) {
        context.dataStore.edit { prefs ->
            if (nowEpochMillis != null) prefs[Keys.CLOUD_LAST_SYNC] = nowEpochMillis
            if (error == null) prefs.remove(Keys.CLOUD_LAST_ERROR) else prefs[Keys.CLOUD_LAST_ERROR] = error
        }
    }

    /** The pairing code on screen and when it stops working; null = none issued. */
    val cloudPairingCode: Flow<Pair<String, Long>?> =
        context.dataStore.data.map { prefs ->
            val code = prefs[Keys.CLOUD_PAIRING_CODE]
            val expires = prefs[Keys.CLOUD_PAIRING_EXPIRES_AT]
            if (code == null || expires == null) null else code to expires
        }

    suspend fun setCloudPairingCode(code: String?, expiresAtEpochMillis: Long?) {
        context.dataStore.edit { prefs ->
            if (code == null || expiresAtEpochMillis == null) {
                prefs.remove(Keys.CLOUD_PAIRING_CODE)
                prefs.remove(Keys.CLOUD_PAIRING_EXPIRES_AT)
            } else {
                prefs[Keys.CLOUD_PAIRING_CODE] = code
                prefs[Keys.CLOUD_PAIRING_EXPIRES_AT] = expiresAtEpochMillis
            }
        }
    }

    /** The address the user linked to the account, as typed; null until then. */
    val cloudEmail: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CLOUD_EMAIL] }

    suspend fun setCloudEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) prefs.remove(Keys.CLOUD_EMAIL) else prefs[Keys.CLOUD_EMAIL] = email
        }
    }

    /** The newest server `updated_at` the pull has applied for [table]; null = never pulled. */
    fun cloudPullWatermark(table: String): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.cloudPullKey(table)] }

    suspend fun setCloudPullWatermark(table: String, value: String) {
        context.dataStore.edit { prefs -> prefs[Keys.cloudPullKey(table)] = value }
    }

    /** Forgets the pull watermarks of [tables], so the next pull starts from the beginning. */
    suspend fun clearCloudPullWatermarks(tables: List<String>) {
        context.dataStore.edit { prefs -> tables.forEach { prefs.remove(Keys.cloudPullKey(it)) } }
    }

    /**
     * Wipes EVERY stored key (used only by the app reset). Clearing beats
     * resetting keys one by one: a key added later can never be forgotten here.
     */
    suspend fun resetAll() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
