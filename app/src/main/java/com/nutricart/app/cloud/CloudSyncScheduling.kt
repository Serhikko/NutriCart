package com.nutricart.app.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nutricart.app.data.settings.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Hands the outbox drain to WorkManager: online only, batched, retried. */
@Singleton
class CloudSyncScheduling @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
) {

    /**
     * Asks for a drain soon. REPLACE plus a short delay turns a burst of writes
     * (a basket, a quick correction) into one request; an already-running
     * worker is not interrupted, WorkManager just queues the replacement.
     */
    fun requestSync(delaySeconds: Long = BATCH_DELAY_SECONDS) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * On app open: whatever is queued goes out now, today's summary refreshes,
     * and the website's edits come down. The periodic run (WorkManager's
     * 15-minute minimum) keeps pulling while the app is in the background, so
     * a meal logged on the website is on the phone by the time it is opened.
     */
    suspend fun reanchor() {
        if (!settings.cloudSyncEnabled.first()) return
        requestSync(delaySeconds = 0)
        ensurePeriodic()
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }

    private companion object {
        const val UNIQUE_NAME = "cloud_sync"
        const val PERIODIC_NAME = "cloud_sync_periodic"
        const val BATCH_DELAY_SECONDS = 10L
        const val PERIOD_MINUTES = 15L
    }
}
