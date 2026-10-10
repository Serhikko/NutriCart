package com.nutricart.app.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.SyncOutboxDao
import com.nutricart.app.data.local.entity.SyncOutboxEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.widget.WidgetDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/**
 * Drains the outbox to Supabase, pulls what the website wrote since the last
 * run, then publishes today's and yesterday's day_summaries and the profile.
 * Runs only online (constraint) and only when sync is on; retries with
 * backoff on network and server trouble; drops a batch the server rejects as
 * malformed (400) after logging it, so one bad row can never block every
 * later write. The order matters: the phone's own writes go up first, so the
 * pull never applies a stale server copy over them.
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outbox: SyncOutboxDao,
    private val auth: CloudAuth,
    private val rest: SupabaseRestApi,
    private val settings: SettingsDataStore,
    private val widgetDataSource: WidgetDataSource,
    private val pull: CloudPull,
    private val mirror: CloudMirror,
    private val profileDao: ProfileDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!CloudConfig.isConfigured || !settings.cloudSyncEnabled.first()) return Result.success()
        val userId = auth.userId() ?: run {
            settings.recordCloudSync(null, ERROR_AUTH)
            return Result.success() // no session: the repository re-signs-in on enable
        }

        return try {
            drainOutbox(userId)
            pull.run(userId, mirror.deviceId()) { auth.bearer() ?: throw Unauthorized() }
            publishSummaries(userId)
            settings.recordCloudSync(System.currentTimeMillis(), null)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Unauthorized) {
            settings.recordCloudSync(null, ERROR_AUTH)
            Result.success()
        } catch (e: IOException) {
            settings.recordCloudSync(null, ERROR_OFFLINE)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: HttpException) {
            Log.w(TAG, "Sync failed with HTTP ${e.code()}")
            settings.recordCloudSync(null, ERROR_SERVER)
            if (e.code() in 500..599 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.javaClass.simpleName}")
            settings.recordCloudSync(null, ERROR_FAILED)
            Result.failure()
        }
    }

    /** Oldest first, one request per table per batch; drained rows are deleted. */
    private suspend fun drainOutbox(userId: String) {
        while (true) {
            val batch = outbox.oldest(BATCH_SIZE)
            if (batch.isEmpty()) return
            batch.groupBy { it.tableName }.forEach { (table, rows) ->
                // Later rows win: the same remote id twice in a batch would be
                // rejected by PostgREST ("ON CONFLICT DO UPDATE command cannot
                // affect row a second time"), so keep only the newest per id.
                val newestPerId = rows.associateBy { it.remoteId }.values
                val payload = JsonArray(newestPerId.map { withOwner(it, table, userId) })
                sendOrDrop(table, payload, rows)
            }
            outbox.deleteByIds(batch.map { it.id })
        }
    }

    private suspend fun sendOrDrop(table: String, payload: JsonArray, rows: List<SyncOutboxEntity>) {
        try {
            upsert(table, payload)
        } catch (e: HttpException) {
            if (e.code() == 400 || e.code() == 422) {
                // Malformed for the server: log the ids, drop the batch, move on.
                Log.w(TAG, "Server rejected ${rows.size} $table rows: HTTP ${e.code()}")
                settings.recordCloudSync(null, ERROR_REJECTED)
            } else {
                throw e
            }
        }
    }

    private suspend fun publishSummaries(userId: String) {
        val today = LocalDate.now().toEpochDay()
        val days = listOfNotNull(widgetDataSource.forDay(today), widgetDataSource.forDay(today - 1))
        if (days.isNotEmpty()) {
            upsert(
                CloudRows.TABLE_DAYS,
                JsonArray(days.map { addOwner(CloudRows.daySummary(it), "owner_id", userId) }),
            )
        }
        settings.cloudDisplayName.first()?.takeIf { it.isNotBlank() }?.let { name ->
            upsert(
                CloudRows.TABLE_PROFILES,
                JsonArray(listOf(addOwner(CloudRows.profile(name), "user_id", userId))),
            )
        }
        // The questionnaire, so the website shows this account's own targets
        // once the user signs in there with the linked email (milestone 3).
        profileDao.observeProfile().first()?.let { profile ->
            upsert(
                CloudRows.TABLE_PROFILE_DETAILS,
                JsonArray(listOf(addOwner(CloudRows.profileDetails(profile), "user_id", userId))),
            )
        }
    }

    /** One upsert with a single token refresh on 401. */
    private suspend fun upsert(table: String, rows: JsonArray) {
        val bearer = auth.bearer() ?: throw Unauthorized()
        val response = rest.upsert(bearer, table, rows)
        if (response.isSuccessful) return
        if (response.code() == 401) {
            val fresh = auth.bearer(force = true) ?: throw Unauthorized()
            val retry = rest.upsert(fresh, table, rows)
            if (retry.isSuccessful) return
            throw HttpException(retry)
        }
        throw HttpException(response)
    }

    private fun withOwner(row: SyncOutboxEntity, table: String, userId: String): JsonObject =
        addOwner(json.parseToJsonElement(row.payloadJson) as JsonObject, CloudRows.ownerColumn(table), userId)

    private fun addOwner(row: JsonObject, column: String, userId: String): JsonObject =
        JsonObject(row + (column to JsonPrimitive(userId)))

    private class Unauthorized : Exception()

    companion object {
        private const val TAG = "CloudSync"
        private const val BATCH_SIZE = 200
        private const val MAX_ATTEMPTS = 6
        private val json = Json { ignoreUnknownKeys = true }

        const val ERROR_OFFLINE = "offline"
        const val ERROR_AUTH = "auth"
        const val ERROR_SERVER = "server"
        const val ERROR_REJECTED = "rejected"
        const val ERROR_FAILED = "failed"
    }
}
