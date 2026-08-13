package com.nutricart.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nutricart.app.R
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.ReminderTimes
import com.nutricart.app.domain.model.MealSlot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Schedules/cancels the self-chaining meal-reminder workers. */
object MealReminderScheduling {

    const val KEY_SLOT = "slot"

    /** The day the run TARGETS — an overdue run must not nag about a new day. */
    const val KEY_TARGET_DAY = "target_day"

    private fun uniqueName(slot: MealSlot) = "meal_reminder_${slot.name}"

    /** (Re)schedules the next occurrence; REPLACE keeps exactly one per slot. */
    fun schedule(context: Context, slot: MealSlot, minutesOfDay: Int) {
        val now = LocalDateTime.now()
        val trigger = ReminderTimes.nextTrigger(now, minutesOfDay)
        val delay = ReminderTimes.delayMillis(now, minutesOfDay)
        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_SLOT to slot.name,
                    KEY_TARGET_DAY to trigger.toLocalDate().toEpochDay(),
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(slot),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, slot: MealSlot) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(slot))
    }

    /**
     * Re-anchors every ENABLED reminder chain. Called on app open: a chain
     * killed by a one-off failure (review-caught) heals itself the next time
     * the user opens the app — same self-repair idea as recurring workouts.
     * REPLACE just re-times the pending run to the same next occurrence.
     */
    suspend fun reanchorAll(context: Context, settings: SettingsDataStore) {
        MealSlot.entries.forEach { slot ->
            if (settings.reminderEnabled(slot).first()) {
                schedule(context, slot, settings.reminderMinutes(slot).first())
            }
        }
    }
}

/**
 * Fires around the configured time, once a day per meal. Two quiet rules:
 * the reminder is SILENT when the meal is already logged (nothing to nag
 * about), and it does nothing when the user disabled it after this run was
 * queued. Each run chains the next one — WorkManager persists the chain
 * across reboots, no exact-alarm permissions needed.
 */
@HiltWorker
class MealReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val foodLogDao: FoodLogDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slotName = inputData.getString(MealReminderScheduling.KEY_SLOT)
            ?: return Result.success()
        val slot = MealSlot.valueOf(slotName)

        return try {
            // Disabled after this run was queued -> stop the chain quietly.
            if (!settings.reminderEnabled(slot).first()) return Result.success()

            val today = LocalDate.now().toEpochDay()
            val targetDay = inputData.getLong(MealReminderScheduling.KEY_TARGET_DAY, today)
            // Three silence rules (all review-hardened):
            //  - an overdue run (Doze, phone off) past midnight targets a day
            //    that is over — never nag about the NEW day's meal;
            //  - once per day per slot, even if the wall clock moved backwards
            //    (DST fall-back / travel) and the chain fired "early";
            //  - the meal is already logged.
            val alreadyNotifiedToday = settings.reminderLastNotifiedDay(slot).first() == today
            if (today == targetDay && !alreadyNotifiedToday &&
                foodLogDao.countForSlot(today, slot) == 0
            ) {
                showNotification(slot)
                settings.setReminderLastNotifiedDay(slot, today)
            }

            // Chain the next run (tomorrow, or today if the time moved forward).
            MealReminderScheduling.schedule(
                applicationContext, slot, settings.reminderMinutes(slot).first(),
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A transient failure (busy DataStore, Room hiccup) must not kill
            // the chain forever: retry re-runs this worker with backoff, and
            // a successful re-run re-chains as usual (review-caught).
            android.util.Log.w("MealReminderWorker", "Reminder run failed, will retry", e)
            Result.retry()
        }
    }

    private fun showNotification(slot: MealSlot) {
        // Without the runtime permission (Android 13+) notify() would throw.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName) ?: return
        val pending = PendingIntent.getActivity(
            applicationContext,
            slot.ordinal,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val slotLabel = applicationContext.getString(slotLabelRes(slot))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.reminder_title))
            .setContentText(applicationContext.getString(R.string.reminder_text, slotLabel))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(slot.ordinal, notification)
    }

    // Creating an existing channel is a no-op, so "ensure on every show" is safe.
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "meal_reminders"

        private fun slotLabelRes(slot: MealSlot): Int = when (slot) {
            MealSlot.BREAKFAST -> R.string.meal_breakfast
            MealSlot.LUNCH -> R.string.meal_lunch
            MealSlot.DINNER -> R.string.meal_dinner
            MealSlot.SNACK -> R.string.meal_snack
        }
    }
}
