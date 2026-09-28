package com.nutricart.app.data.repository

import com.nutricart.app.cloud.CloudMirror
import com.nutricart.app.cloud.CloudSyncScheduling
import com.nutricart.app.data.health.HcAvailability
import com.nutricart.app.data.health.HealthConnectManager
import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.dao.WorkoutDao
import com.nutricart.app.data.local.entity.DailyActivityEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.local.entity.WorkoutEntryEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.WeightSource
import com.nutricart.app.domain.model.WorkoutSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What a sync attempt ended with; the dashboard shows a matching banner. */
enum class SyncResult { SUCCESS, NOT_INSTALLED, UPDATE_REQUIRED, NO_PERMISSION, ERROR }

/**
 * Activity data for the dashboard. The UI always reads the LOCAL cache
 * (daily_activity table); syncNow() only refreshes that cache.
 */
@Singleton
class ActivityRepository @Inject constructor(
    private val healthConnect: HealthConnectManager,
    private val activityDao: ActivityDao,
    private val weightDao: WeightDao,
    private val workoutDao: WorkoutDao,
    private val settings: SettingsDataStore,
    private val cloudMirror: CloudMirror,
    private val cloudSyncScheduling: CloudSyncScheduling,
) {

    fun observeDay(epochDay: Long): Flow<DailyActivityEntity?> =
        activityDao.observeDay(epochDay)

    /** When the last successful sync finished (null = never). */
    fun observeLastSync(): Flow<Long?> = settings.lastHcSyncEpochMillis

    /**
     * THE one sync implementation. Both the hourly background worker and the
     * dashboard's pull-to-refresh call this — one logic, two triggers.
     */
    suspend fun syncNow(): SyncResult {
        when (healthConnect.availability()) {
            HcAvailability.NOT_INSTALLED -> return SyncResult.NOT_INSTALLED
            HcAvailability.UPDATE_REQUIRED -> return SyncResult.UPDATE_REQUIRED
            HcAvailability.AVAILABLE -> Unit // continue below
        }

        return try {
            val granted = healthConnect.grantedPermissions()
            if (!healthConnect.hasRequiredPermissions(granted)) return SyncResult.NO_PERMISSION

            val today = LocalDate.now()
            // Refresh the last 7 days: older days can still change when the
            // watch uploads its data late.
            for (offset in 6 downTo 0) {
                val day = today.minusDays(offset.toLong())
                val summary = healthConnect.readDay(day, granted)
                activityDao.upsert(
                    DailyActivityEntity(
                        epochDay = day.toEpochDay(),
                        steps = summary.steps,
                        activeKcal = summary.activeKcal,
                        exerciseMinutes = summary.exerciseMinutes,
                        sleepMinutes = summary.sleepMinutes,
                        avgHeartRateBpm = summary.avgHeartRateBpm,
                    )
                )

                // Watch workout sessions for the same day. null = the exercise
                // permission is denied ("cannot know") — keep the cached rows;
                // an empty list genuinely means "no workouts" and clears them.
                healthConnect.readExerciseSessions(day, granted)?.let { sessions ->
                    workoutDao.replaceHealthConnectDay(
                        day.toEpochDay(),
                        sessions.map { s ->
                            WorkoutEntryEntity(
                                epochDay = day.toEpochDay(),
                                source = WorkoutSource.HEALTH_CONNECT,
                                hcSessionId = s.id,
                                hcExerciseType = s.exerciseType,
                                type = null,
                                title = s.title,
                                minutes = s.minutes,
                                reps = null,
                                kcal = s.kcal,
                                // Session start, so the day's list sorts by
                                // when the workout actually happened.
                                loggedAtEpochMillis = s.startEpochMillis,
                            )
                        },
                    )
                }
            }

            // Weight from the watch/scale (only when that permission was
            // granted). Stored with source HEALTH_CONNECT: a MANUAL entry for
            // the same day always wins over it in queries.
            if (healthConnect.canReadWeight(granted)) healthConnect.readLatestWeight(today)?.let { (date, weightKg) ->
                val entry = WeightEntryEntity(
                    epochDay = date.toEpochDay(),
                    weightKg = weightKg,
                    source = WeightSource.HEALTH_CONNECT,
                )
                weightDao.insert(entry)
                cloudMirror.weightLogged(entry)
            }

            settings.setLastHcSyncEpochMillis(System.currentTimeMillis())
            // Steps and active kcal changed: the website's day summary should
            // follow (the worker does nothing when sync is off).
            if (settings.cloudSyncEnabled.first()) cloudSyncScheduling.requestSync()
            SyncResult.SUCCESS
        } catch (e: CancellationException) {
            // Never swallow a coroutine cancellation — rethrow so the caller
            // (a stopped worker or a closed screen) can finish cancelling.
            throw e
        } catch (e: SecurityException) {
            // Thrown for a background read without the extra background permission.
            SyncResult.NO_PERMISSION
        } catch (e: Exception) {
            // Health Connect lives in another process; transient IO/remote errors
            // are possible and simply mean "try again later".
            android.util.Log.w("ActivityRepository", "Health Connect sync failed", e)
            SyncResult.ERROR
        }
    }
}
