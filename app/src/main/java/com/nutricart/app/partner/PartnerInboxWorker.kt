package com.nutricart.app.partner

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
import androidx.work.WorkerParameters
import com.nutricart.app.R
import com.nutricart.app.cloud.CloudRepository
import com.nutricart.app.data.settings.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Reads what the partner wrote to the bot and turns it into something on this
 * phone: `/today` is answered with the day's digest, anything else becomes a
 * notification — that is the "remind me to eat" half of the feature, in the
 * partner's own words. Runs every 15 minutes (WorkManager's floor) and once on
 * every app open, so the worst-case delay is what Settings promises.
 */
@HiltWorker
class PartnerInboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: PartnerRepository,
    private val cloud: CloudRepository,
    private val settings: SettingsDataStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Nudges from the website: same notification, a different inbox.
        cloud.fetchUnseenNudges().forEach { nudge ->
            val sender = nudge.fromName.ifBlank { applicationContext.getString(R.string.cloud_partner_default_name) }
            notify(nudge.id.hashCode().toLong(), sender, nudge.text)
        }

        if (!settings.partnerInboxEnabled.first()) return Result.success()
        val inbox = repository.fetchPartnerMessages()
        inbox.messages.forEach { message ->
            when {
                message.text.startsWith("/today", ignoreCase = true) -> {
                    val title = applicationContext.getString(
                        R.string.partner_day_title,
                        LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    )
                    repository.sendDayDigest(title, partnerLabels(applicationContext))
                }
                // /start is Telegram's handshake, not a message to the user.
                message.text.startsWith("/start", ignoreCase = true) -> Unit
                message.text.startsWith("/nudge", ignoreCase = true) -> notify(
                    message.updateId, message.senderName,
                    applicationContext.getString(R.string.partner_nudge_default),
                )
                else -> notify(message.updateId, message.senderName, message.text)
            }
        }
        return when (inbox.error) {
            PartnerResult.Offline, PartnerResult.Busy ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private fun notify(updateId: Long, sender: String, text: String) {
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
            NOTIFICATION_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        // One notification per message (the id is derived from the update),
        // so two nudges in a row do not overwrite each other.
        NotificationManagerCompat.from(applicationContext)
            .notify((updateId and 0x7fffffff).toInt(), notification)
    }

    // Creating an existing channel is a no-op, so "ensure on every show" is safe.
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.partner_channel_name),
            // A person wrote this — it should be heard, unlike a scheduled reminder.
            NotificationManager.IMPORTANCE_HIGH,
        )
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "partner_messages"
        private const val NOTIFICATION_REQUEST_CODE = 900
        private const val MAX_ATTEMPTS = 3
    }
}
