package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.WeightSource
import kotlinx.coroutines.flow.Flow
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
) {
    fun observeProfile(): Flow<UserProfileEntity?> = profileDao.observeProfile()

    fun observeLatestWeight(): Flow<WeightEntryEntity?> = weightDao.observeLatest()

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
