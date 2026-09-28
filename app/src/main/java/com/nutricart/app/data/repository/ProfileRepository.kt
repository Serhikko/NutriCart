package com.nutricart.app.data.repository

import com.nutricart.app.cloud.CloudMirror
import com.nutricart.app.cloud.CloudSyncScheduling
import com.nutricart.app.data.local.AppDatabase
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.settings.SecretsDataStore
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.WeightSource
import com.nutricart.app.partner.PartnerScheduling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for everything about the user's profile and weight.
 * ViewModels talk to this class, never to DAOs directly.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val weightDao: WeightDao,
    private val settings: SettingsDataStore,
    private val secrets: SecretsDataStore,
    private val db: AppDatabase,
    private val partnerScheduling: PartnerScheduling,
    private val cloudMirror: CloudMirror,
    private val cloudSyncScheduling: CloudSyncScheduling,
) {
    fun observeProfile(): Flow<UserProfileEntity?> = profileDao.observeProfile()

    fun observeLatestWeight(): Flow<WeightEntryEntity?> = weightDao.observeLatest()

    /** Full weight history (both sources) — the chart dedupes per day itself. */
    fun observeWeightHistory(): Flow<List<WeightEntryEntity>> = weightDao.observeAll()

    /** Overwrites the single profile row (used by the settings screen). */
    suspend fun updateProfile(profile: UserProfileEntity) {
        profileDao.upsert(profile)
    }

    /** Adds (or replaces) today's manual weight entry — history stays intact. */
    suspend fun logWeight(weightKg: Double, todayEpochDay: Long) {
        val entry = WeightEntryEntity(
            epochDay = todayEpochDay,
            weightKg = weightKg,
            source = WeightSource.MANUAL,
        )
        weightDao.insert(entry)
        cloudMirror.weightLogged(entry)
    }

    /**
     * "Reset the app": wipes ALL saved data — every Room table (diary, plan,
     * shopping list, activity, workouts, water, recipes...) and every DataStore
     * key — and thereby clears the onboarding flag, which sends the UI back to
     * the questionnaire. clearAllTables() beats per-DAO deletes: a table added
     * later can never be forgotten here (that exact bug shipped in v0.2–v0.8).
     * Recipes re-seed from assets on the next screen that touches them.
     */
    suspend fun resetAll() {
        // NonCancellable: once started, the reset must run to completion even if
        // the calling screen dies mid-way. A half-done reset could leave
        // "onboarding completed" pointing at an already-empty database.
        // IO dispatcher: clearAllTables() is a blocking call.
        withContext(NonCancellable + Dispatchers.IO) {
            db.clearAllTables()
            settings.resetAll()
            // The second DataStore file is exactly the kind of store that gets
            // forgotten here — the same class of bug as per-DAO deletes.
            secrets.resetAll()
            // The partner's queued and periodic jobs would otherwise run once
            // more against an empty store and a missing token. Same for the
            // cloud drain (clearAllTables emptied its outbox already).
            partnerScheduling.cancelAll()
            cloudSyncScheduling.cancel()
        }
    }

    /**
     * Saves the whole onboarding result:
     * profile row + the first weight entry + the "onboarding done" flag.
     */
    suspend fun completeOnboarding(
        profile: UserProfileEntity,
        weightKg: Double,
        todayEpochDay: Long,
    ) {
        profileDao.upsert(profile)
        weightDao.insert(
            WeightEntryEntity(
                epochDay = todayEpochDay,
                weightKg = weightKg,
                source = WeightSource.MANUAL,
            )
        )
        settings.setOnboardingCompleted(true)
    }
}
