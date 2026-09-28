package com.nutricart.app.cloud

import android.util.Log
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.SyncOutboxDao
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.PairingCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a cloud action ended, for Settings to show. */
sealed interface CloudResult {
    data object Ok : CloudResult
    /** This build has no Supabase keys. */
    data object NotConfigured : CloudResult
    data object Offline : CloudResult
    /** The session is gone and could not be renewed. */
    data object Auth : CloudResult
    data object Failed : CloudResult
}

/** One person who redeemed this account's pairing code. */
data class CloudPartner(
    val linkId: String,
    val name: String,
    val sinceEpochMillis: Long?,
)

/**
 * Everything Settings does with the cloud: switch sync on (sign in, backfill,
 * publish the profile), issue pairing codes, list and unlink partners, and
 * read what partners sent. The sync itself lives in CloudSyncWorker.
 */
@Singleton
class CloudRepository @Inject constructor(
    private val auth: CloudAuth,
    private val rest: SupabaseRestApi,
    private val settings: SettingsDataStore,
    private val outbox: SyncOutboxDao,
    private val mirror: CloudMirror,
    private val scheduling: CloudSyncScheduling,
    private val foodLogDao: FoodLogDao,
    private val waterDao: WaterDao,
    private val weightDao: WeightDao,
) {

    val isConfigured: Boolean get() = CloudConfig.isConfigured
    val isEnabled: Flow<Boolean> = settings.cloudSyncEnabled
    val displayName: Flow<String?> = settings.cloudDisplayName
    val pairingCode: Flow<Pair<String, Long>?> = settings.cloudPairingCode
    val lastSyncEpochMillis: Flow<Long?> = settings.cloudLastSyncEpochMillis
    val lastError: Flow<String?> = settings.cloudLastError
    val pendingCount: Flow<Int> = outbox.observeCount()

    /**
     * Switches sync on: signs in (anonymously, once), stores the name, queues
     * the last [BACKFILL_DAYS] days so the partner sees history, and asks for
     * the first drain. Idempotent: enabling twice queues nothing twice.
     */
    suspend fun enable(displayName: String): CloudResult {
        if (!isConfigured) return CloudResult.NotConfigured
        val name = displayName.trim().take(40)
        if (name.isEmpty()) return CloudResult.Failed
        return call {
            auth.ensureSignedIn()
            settings.setCloudDisplayName(name)
            val wasEnabled = settings.cloudSyncEnabled.first()
            settings.setCloudSyncEnabled(true)
            if (!wasEnabled) backfill()
            scheduling.requestSync(delaySeconds = 0)
            CloudResult.Ok
        }
    }

    /** Switches sync off: pending writes are dropped, the account and its data stay. */
    suspend fun disable() {
        settings.setCloudSyncEnabled(false)
        scheduling.cancel()
        outbox.clear()
        settings.setCloudPairingCode(null, null)
    }

    suspend fun setDisplayName(displayName: String): CloudResult {
        val name = displayName.trim().take(40)
        if (name.isEmpty()) return CloudResult.Failed
        settings.setCloudDisplayName(name)
        scheduling.requestSync(delaySeconds = 0) // the worker publishes the profile
        return CloudResult.Ok
    }

    /**
     * A fresh pairing code: older unused codes of this account are discarded
     * first, so exactly one code works at a time. The plain code is kept on
     * the phone for display only; the server sees its hash.
     */
    suspend fun newPairingCode(): CloudResult {
        if (!isConfigured) return CloudResult.NotConfigured
        return call {
            val userId = auth.ensureSignedIn()
            val bearer = bearerOrThrow()
            rest.delete(bearer, CloudRows.TABLE_PAIRING_CODES, mapOf("owner_id" to "eq.$userId")).requireSuccess()
            val code = PairingCode.generate()
            val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(PairingCode.VALIDITY_MINUTES)
            val row = JsonObject(
                CloudRows.pairingCode(PairingCode.hash(code), expiresAt) + ("owner_id" to JsonPrimitive(userId))
            )
            rest.upsert(bearer, CloudRows.TABLE_PAIRING_CODES, JsonArray(listOf(row))).requireSuccess()
            settings.setCloudPairingCode(code, expiresAt)
            CloudResult.Ok
        }
    }

    /** Who can currently read this account. */
    suspend fun partners(): List<CloudPartner> {
        if (!isConfigured) return emptyList()
        val userId = auth.userId() ?: return emptyList()
        return try {
            rest.partnerLinks(bearerOrThrow(), "eq.$userId").map { link ->
                CloudPartner(
                    linkId = link.id,
                    name = link.partner?.displayName?.takeIf { it.isNotBlank() } ?: link.partnerId.take(8),
                    sinceEpochMillis = runCatching { java.time.Instant.parse(link.createdAt).toEpochMilli() }.getOrNull(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not list partners: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    suspend fun unlink(linkId: String): CloudResult = call {
        rest.delete(bearerOrThrow(), "partner_links", mapOf("id" to "eq.$linkId")).requireSuccess()
        CloudResult.Ok
    }

    /**
     * Nudges the phone has not shown yet, marked seen on the server in the
     * same call so a second poll never repeats them.
     */
    suspend fun fetchUnseenNudges(): List<SbNudgeDto> {
        if (!isConfigured || !settings.cloudSyncEnabled.first()) return emptyList()
        val userId = auth.userId() ?: return emptyList()
        return try {
            val bearer = bearerOrThrow()
            val nudges = rest.unseenNudges(bearer, "eq.$userId")
            if (nudges.isNotEmpty()) {
                val ids = nudges.joinToString(",") { it.id }
                rest.patchNudges(
                    bearer, "in.($ids)",
                    buildJsonObject { put("seen_at", CloudRows.iso(System.currentTimeMillis())) },
                ).requireSuccess()
            }
            nudges
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read nudges: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** The last 90 days into the outbox, in one insert per table. */
    private suspend fun backfill() {
        val today = LocalDate.now().toEpochDay()
        val from = today - BACKFILL_DAYS
        val device = mirror.deviceId()
        val rows = buildList {
            foodLogDao.entriesBetween(from, today).forEach { e ->
                add(mirror.row(CloudRows.TABLE_FOOD, CloudRows.foodId(device, e.id), CloudRows.foodEntry(device, e)))
            }
            waterDao.entriesBetween(from, today).forEach { e ->
                add(mirror.row(CloudRows.TABLE_WATER, CloudRows.waterId(device, e.id), CloudRows.waterEntry(device, e)))
            }
            weightDao.all().filter { it.epochDay >= from }.forEach { e ->
                add(mirror.row(CloudRows.TABLE_WEIGHT, "${e.epochDay}:${e.source.name}", CloudRows.weightEntry(e)))
            }
        }
        mirror.queueRows(rows)
    }

    private suspend fun bearerOrThrow(): String = auth.bearer() ?: throw NotSignedIn()

    private fun retrofit2.Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }

    private class NotSignedIn : Exception()

    /** Maps every failure of a Settings action to a CloudResult; class names only in logs. */
    private suspend fun call(block: suspend () -> CloudResult): CloudResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotSignedIn) {
        CloudResult.Auth
    } catch (e: HttpException) {
        Log.w(TAG, "Cloud call failed with HTTP ${e.code()}")
        if (e.code() == 401 || e.code() == 403) CloudResult.Auth else CloudResult.Failed
    } catch (e: SerializationException) {
        Log.w(TAG, "Cloud answer could not be read: ${e.javaClass.simpleName}")
        CloudResult.Failed
    } catch (e: IOException) {
        CloudResult.Offline
    } catch (e: Exception) {
        Log.w(TAG, "Cloud call failed: ${e.javaClass.simpleName}")
        CloudResult.Failed
    }

    private companion object {
        const val TAG = "Cloud"
        const val BACKFILL_DAYS = 90L
    }
}
