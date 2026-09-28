package com.nutricart.app.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

    /** On app open: whatever is queued goes out now, and today's summary refreshes. */
    suspend fun reanchor() {
        if (settings.cloudSyncEnabled.first()) requestSync(delaySeconds = 0)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private companion object {
        const val UNIQUE_NAME = "cloud_sync"
        const val BATCH_DELAY_SECONDS = 10L
    }
}
