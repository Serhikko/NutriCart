package com.nutricart.app.partner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nutricart.app.data.settings.SecretsDataStore
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.MealSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides WHEN the partner hears something and hands the work to WorkManager.
 * Nothing here touches the network: the workers do, with a connectivity
 * constraint and retries, so logging a meal offline still reaches the
 * partner once the phone is back online.
 */
@Singleton
class PartnerScheduling @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val secrets: SecretsDataStore,
) {

    private suspend fun linked(): Boolean =
        settings.partnerLink.first() != null && !secrets.telegramBotToken.first().isNullOrBlank()

    /**
     * Called after every diary write. Only today's meals are announced —
     * filling in yesterday's forgotten dinner is bookkeeping, not eating.
     * REPLACE + a short delay collapses a basket of five items, or a quick
     * correction, into ONE message per meal.
     */
    suspend fun onMealLogged(slot: MealSlot, epochDay: Long) {
        if (epochDay != LocalDate.now().toEpochDay()) return
        if (!linked() || !settings.partnerShareMeals.first()) return
        val request = OneTimeWorkRequestBuilder<PartnerSendWorker>()
            .setInitialDelay(MEAL_BATCH_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(online())
            .setInputData(PartnerSendWorker.mealInput(slot, epochDay))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "partner_meal_${slot.name}", ExistingWorkPolicy.REPLACE, request,
        )
    }

    /** Called by the meal reminder when it fires for an unlogged meal. */
    suspend fun onMealMissed(slot: MealSlot) {
        if (!linked() || !settings.partnerNotifyMissed.first()) return
        val request = OneTimeWorkRequestBuilder<PartnerSendWorker>()
            .setConstraints(online())
            .setInputData(PartnerSendWorker.missedInput(slot))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "partner_missed_${slot.name}", ExistingWorkPolicy.REPLACE, request,
        )
    }

    /**
     * Keeps the inbox polling in step with the settings: a linked partner
     * with the inbox on gets the 15-minute cycle (WorkManager's minimum) plus
     * an immediate poll; anything else cancels the cycle. Called on app open
     * and after every settings change, so a broken schedule self-repairs the
     * same way the meal reminders do.
     */
    suspend fun reanchor() {
        val wm = WorkManager.getInstance(context)
        val telegram = linked() && settings.partnerInboxEnabled.first()
        // The cloud inbox (nudges from the website) rides the same cycle.
        val cloud = settings.cloudSyncEnabled.first()
        if (telegram || cloud) {
            val periodic = PeriodicWorkRequestBuilder<PartnerInboxWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online())
                .build()
            wm.enqueueUniquePeriodicWork(INBOX_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
            pollNow()
        } else {
            wm.cancelUniqueWork(INBOX_PERIODIC)
        }
    }

    /** One extra poll right now — on app open, so a nudge sent an hour ago shows at once. */
    fun pollNow() {
        val request = OneTimeWorkRequestBuilder<PartnerInboxWorker>()
            .setConstraints(online())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            INBOX_NOW, ExistingWorkPolicy.REPLACE, request,
        )
    }

    /** Part of "reset the app" and of unlinking. */
    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(INBOX_PERIODIC)
        wm.cancelUniqueWork(INBOX_NOW)
        MealSlot.entries.forEach { slot ->
            wm.cancelUniqueWork("partner_meal_${slot.name}")
            wm.cancelUniqueWork("partner_missed_${slot.name}")
        }
    }

    private fun online() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val MEAL_BATCH_DELAY_SECONDS = 45L
        const val INBOX_PERIODIC = "partner_inbox"
        const val INBOX_NOW = "partner_inbox_now"
    }
}
