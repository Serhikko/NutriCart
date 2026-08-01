package com.nutricart.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nutricart.app.data.repository.ActivityRepository
import com.nutricart.app.data.repository.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Hourly background sync. The worker itself contains NO sync logic —
 * it just calls the same ActivityRepository.syncNow() as pull-to-refresh.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val activityRepository: ActivityRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        when (activityRepository.syncNow()) {
            // Temporary problem (Health Connect busy, etc.) — retry with backoff.
            SyncResult.ERROR -> Result.retry()
            // Success, or a state the user must fix in the UI (not installed /
            // no permission) — retrying would not help, so report success.
            else -> Result.success()
        }

    companion object {
        private const val UNIQUE_NAME = "hc_sync"

        /** Schedules the hourly sync. KEEP = never stack a duplicate schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
