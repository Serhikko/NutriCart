package com.nutricart.app.cloud

import android.util.Log
import com.nutricart.app.data.settings.CloudSession
import com.nutricart.app.data.settings.SecretsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's anonymous Supabase user. Signs up once, keeps the session in
 * the secrets store, and hands out a valid bearer token, refreshing it when
 * it is about to expire. A [Mutex] makes sure two workers never refresh the
 * same token at once (the second refresh would invalidate the first).
 */
@Singleton
class CloudAuth @Inject constructor(
    private val authApi: SupabaseAuthApi,
    private val secrets: SecretsDataStore,
) {
    private val refreshLock = Mutex()

    suspend fun session(): CloudSession? = secrets.cloudSession.first()

    suspend fun userId(): String? = session()?.userId

    /** Signs up an anonymous user if there is no session yet; returns the user id. */
    suspend fun ensureSignedIn(): String {
        session()?.let { return it.userId }
        val fresh = authApi.signUpAnonymous(JsonObject(emptyMap()))
        val stored = fresh.toSession()
        secrets.setCloudSession(stored)
        return stored.userId
    }

    /**
     * "Bearer <token>" for the next request, or null when not signed in.
     * Refreshes when less than a minute of validity is left; [force] refreshes
     * regardless (after a 401).
     */
    suspend fun bearer(force: Boolean = false): String? = refreshLock.withLock {
        val current = session() ?: return@withLock null
        val stale = current.expiresAtEpochMillis - System.currentTimeMillis() < 60_000L
        if (!force && !stale) return@withLock "Bearer ${current.accessToken}"
        try {
            val refreshed = authApi.refresh(SbRefreshRequest(current.refreshToken)).toSession()
            secrets.setCloudSession(refreshed)
            "Bearer ${refreshed.accessToken}"
        } catch (e: HttpException) {
            // A dead refresh token (revoked, or the project was reset) cannot be
            // repaired here; the repository re-signs-in on the next enable.
            Log.w(TAG, "Token refresh rejected with HTTP ${e.code()}")
            if (e.code() in 400..499) secrets.clearCloudSession()
            null
        }
    }

    suspend fun signOut() {
        secrets.clearCloudSession()
    }

    private fun SbSessionDto.toSession() = CloudSession(
        userId = user.id,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochMillis = System.currentTimeMillis() + expiresIn * 1000L,
    )

    private companion object {
        const val TAG = "CloudAuth"
    }
}
