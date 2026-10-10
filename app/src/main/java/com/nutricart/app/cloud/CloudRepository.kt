package com.nutricart.app.cloud

import android.util.Log
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.SyncOutboxDao
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.PairingCode
import com.nutricart.app.domain.logic.PasswordGenerator
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

/** The email state of the cloud account, for Settings. */
data class CloudAccount(
    /** The confirmed address, or null while the account is anonymous. */
    val email: String?,
    /** An address whose confirmation mail has been sent but not clicked yet. */
    val pendingEmail: String?,
)

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
    private val authApi: SupabaseAuthApi,
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
    /** The address the user typed when linking; shown until the server confirms it. */
    val linkedEmail: Flow<String?> = settings.cloudEmail

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
            if (!wasEnabled) {
                backfill()
                // Pull everything the website may already hold for this account.
                settings.clearCloudPullWatermarks(listOf(CloudRows.TABLE_FOOD, CloudRows.TABLE_WATER, CloudRows.TABLE_WEIGHT))
            }
            scheduling.requestSync(delaySeconds = 0)
            scheduling.ensurePeriodic()
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

    /**
     * Links an email and a password to the (anonymous) account. GoTrue mails a
     * confirmation link; once clicked, the same address and password sign in
     * on the website from any browser and open this account there, and the
     * account survives a lost phone. The password is never stored on the phone.
     */
    suspend fun linkEmail(email: String, password: String): CloudResult {
        if (!isConfigured) return CloudResult.NotConfigured
        val address = email.trim().lowercase()
        if (!EMAIL.matches(address) || !PasswordGenerator.isAcceptable(password)) return CloudResult.Failed
        return call {
            auth.ensureSignedIn()
            val body = buildJsonObject {
                put("email", address)
                put("password", password)
            }
            authApi.updateUser(bearerOrThrow(), body)
            settings.setCloudEmail(address)
            CloudResult.Ok
        }
    }

    /** What the server says about the account's email; null when it cannot be asked right now. */
    suspend fun account(): CloudAccount? {
        if (!isConfigured) return null
        if (auth.userId() == null) return null
        return try {
            val user = authApi.user(bearerOrThrow())
            val confirmed = user.email?.takeIf { it.isNotBlank() && user.emailConfirmedAt != null && !user.isAnonymous }
            CloudAccount(email = confirmed, pendingEmail = user.newEmail ?: user.email?.takeIf { confirmed == null && it.isNotBlank() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the account: ${e.javaClass.simpleName}")
            null
        }
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
     * the phone for display only; the server sees its hash. The server sets
     * the 15-minute expiry from its own clock (0007); the one sent here is
     * overwritten and only the countdown uses it.
     */
    suspend fun newPairingCode(): CloudResult {
        if (!isConfigured) return CloudResult.NotConfigured
        return call {
            val userId = auth.ensureSignedIn()
            val bearer = bearerOrThrow()
            publishName(bearer, userId)
            rest.delete(bearer, CloudRows.TABLE_PAIRING_CODES, mapOf("owner_id" to "eq.$userId")).requireSuccess()
            val code = PairingCode.generate()
            val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(PairingCode.VALIDITY_MINUTES)
            val row = JsonObject(
                CloudRows.pairingCode(PairingCode.hash(code), expiresAt) + ("owner_id" to JsonPrimitive(userId))
            )
            // A plain insert: a code row is never updated, and an upsert would also need the
            // owner's SELECT policy, which a database without 0007 does not have.
            rest.insert(bearer, CloudRows.TABLE_PAIRING_CODES, JsonArray(listOf(row))).requireSuccess()
            settings.setCloudPairingCode(code, expiresAt)
            CloudResult.Ok
        }
    }

    /**
     * The stored name, published before a code is made: the worker publishes it too, but on the
     * first enable only after the backfill has drained, and a partner who redeems in that window
     * would see the server's stand-in ("NutriCart"). Best effort: a refusal here does not stop
     * the code.
     */
    private suspend fun publishName(bearer: String, userId: String) {
        val name = settings.cloudDisplayName.first()?.takeIf { it.isNotBlank() } ?: return
        val row = JsonObject(CloudRows.profile(name) + ("user_id" to JsonPrimitive(userId)))
        val response = rest.upsert(bearer, CloudRows.TABLE_PROFILES, JsonArray(listOf(row)))
        if (!response.isSuccessful) Log.w(TAG, "Could not publish the name: HTTP ${response.code()}")
    }

    /** Who can currently read this account; null when the list could not be loaded. */
    suspend fun partners(): List<CloudPartner>? {
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
            null
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
                add(mirror.row(CloudRows.TABLE_FOOD, CloudRows.foodId(device, e), CloudRows.foodEntry(device, e)))
            }
            waterDao.entriesBetween(from, today).forEach { e ->
                add(mirror.row(CloudRows.TABLE_WATER, CloudRows.waterId(device, e), CloudRows.waterEntry(device, e)))
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
        cloudResultFor(e)
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
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/**
 * What an HTTP error of a Settings action means, by where the answer came from. GoTrue (auth/v1)
 * answers a bad or expired JWT, a revoked session and a deleted user with 401 or 403: the session
 * is dead. PostgREST (rest/v1) uses 401 for the JWT and 403 for row-level security or a grant
 * refusing the request (42501), which a new session would not change.
 */
internal fun cloudResultFor(e: HttpException): CloudResult {
    val fromRest = e.response()?.raw()?.request?.url?.encodedPath?.contains("/rest/v1/") == true
    return if (e.code() == 401 || (e.code() == 403 && !fromRest)) CloudResult.Auth else CloudResult.Failed
}
