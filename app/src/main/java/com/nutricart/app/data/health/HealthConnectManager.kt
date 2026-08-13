package com.nutricart.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The three situations the UI must handle before any data can be read. */
enum class HcAvailability { NOT_INSTALLED, UPDATE_REQUIRED, AVAILABLE }

/**
 * One day of watch/phone activity, already summed up.
 * null = Health Connect had NO data of that kind for the day; this must never
 * be turned into a fake 0, because "no watch data" and "measured zero" lead to
 * different target formulas.
 */
data class DayActivitySummary(
    val steps: Int?,
    val activeKcal: Double?,
    val exerciseMinutes: Int?,
    val sleepMinutes: Int?,
    val avgHeartRateBpm: Int?,
)

/**
 * One workout session recorded by the watch (a run, a gym visit, ...).
 * kcal can be null: the watch logged the session but reported no calories
 * for its time window — "no data", not 0.
 */
data class ExerciseSessionSummary(
    /** Health Connect record id — stable across syncs, used for de-duplication. */
    val id: String,
    /** User-visible name from the watch app (often null). */
    val title: String?,
    /** Raw ExerciseSessionRecord.EXERCISE_TYPE_* constant. */
    val exerciseType: Int,
    val startEpochMillis: Long,
    val minutes: Int,
    val kcal: Double?,
)

/**
 * The only class that talks to the Health Connect API directly.
 * Everything above it (repository, ViewModels) works with plain Kotlin types.
 *
 * IMPORTANT: we read ActiveCaloriesBurned ONLY and later add it to BMR.
 * TotalCaloriesBurned must NOT be combined with BMR: "total" already contains
 * the resting (BMR) part, so BMR + total would count resting energy twice.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val activeCaloriesPermission =
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val exercisePermission =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)

    private val dataTypePermissions: Set<String> = setOf(
        stepsPermission,
        activeCaloriesPermission,
        heartRatePermission,
        exercisePermission,
        sleepPermission,
        weightPermission,
    )

    /**
     * Sync REQUIRES only steps + active calories (they feed the target math).
     * Everything else is optional: a user who denies e.g. sleep still gets a
     * fully working sync — the sleep row just stays empty.
     */
    private val requiredPermissions: Set<String> =
        setOf(stepsPermission, activeCaloriesPermission)

    fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HcAvailability.UPDATE_REQUIRED
            else -> HcAvailability.NOT_INSTALLED
        }

    // Only touch the client when availability() said AVAILABLE.
    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(context)

    /**
     * What the permission dialog should ask for. Background read is included
     * ONLY when this Health Connect version supports it — requesting an
     * unknown permission can break the whole dialog on older versions.
     */
    fun permissionsToRequest(): Set<String> {
        if (availability() != HcAvailability.AVAILABLE) return dataTypePermissions
        val backgroundSupported = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (backgroundSupported) {
            dataTypePermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else {
            dataTypePermissions
        }
    }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    fun hasRequiredPermissions(granted: Set<String>): Boolean =
        granted.containsAll(requiredPermissions)

    fun canReadWeight(granted: Set<String>): Boolean = weightPermission in granted

    /**
     * Daily totals via the AGGREGATE API. Aggregation is important: if both the
     * phone and the watch recorded the same walk, aggregate() de-duplicates the
     * overlap — summing raw records ourselves would count it twice.
     * Only GRANTED record types are requested; the rest stay null.
     */
    suspend fun readDay(day: LocalDate, granted: Set<String>): DayActivitySummary {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()

        val metrics = buildSet {
            if (stepsPermission in granted) add(StepsRecord.COUNT_TOTAL)
            if (activeCaloriesPermission in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (exercisePermission in granted) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
            if (sleepPermission in granted) add(SleepSessionRecord.SLEEP_DURATION_TOTAL)
            if (heartRatePermission in granted) add(HeartRateRecord.BPM_AVG)
        }
        if (metrics.isEmpty()) {
            return DayActivitySummary(null, null, null, null, null)
        }

        val result = client.aggregate(
            AggregateRequest(
                metrics = metrics,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )

        // A null aggregate means "nothing recorded" — keep it null, do NOT
        // replace it with 0 (see DayActivitySummary's documentation).
        return DayActivitySummary(
            steps = result[StepsRecord.COUNT_TOTAL]?.toInt(),
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories,
            exerciseMinutes = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
                ?.toMinutes()?.toInt(),
            sleepMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                ?.toMinutes()?.toInt(),
            avgHeartRateBpm = result[HeartRateRecord.BPM_AVG]?.toInt(),
        )
    }

    /**
     * The day's workout sessions. Returns NULL when the exercise permission is
     * missing ("cannot know"), an empty list when there genuinely were none —
     * the caller must not wipe its cache in the null case.
     *
     * A session is assigned to the day its START falls on: readRecords returns
     * everything OVERLAPPING the window, so without the start filter a session
     * crossing midnight would be returned for both days and break the unique
     * hcSessionId index.
     */
    suspend fun readExerciseSessions(
        day: LocalDate,
        granted: Set<String>,
    ): List<ExerciseSessionSummary>? {
        if (exercisePermission !in granted) return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        return response.records
            .filter { it.startTime >= start && it.startTime < end }
            .map { record ->
                // kcal for the session = aggregate over ITS OWN time window, so
                // Health Connect de-duplicates phone/watch overlap exactly like
                // it does for the daily total.
                val kcal = if (activeCaloriesPermission in granted) {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                        )
                    )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                } else {
                    null
                }
                ExerciseSessionSummary(
                    id = record.metadata.id,
                    title = record.title,
                    exerciseType = record.exerciseType,
                    startEpochMillis = record.startTime.toEpochMilli(),
                    minutes = Duration.between(record.startTime, record.endTime).toMinutes().toInt(),
                    kcal = kcal,
                )
            }
    }

    /** Newest weight measurement from the last 30 days, or null if none. */
    suspend fun readLatestWeight(today: LocalDate): Pair<LocalDate, Double>? {
        val zone = ZoneId.systemDefault()
        val start = today.minusDays(30).atStartOfDay(zone).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.after(start),
                ascendingOrder = false, // newest first
                pageSize = 1,
            )
        )
        val record = response.records.firstOrNull() ?: return null
        return record.time.atZone(zone).toLocalDate() to record.weight.inKilograms
    }
}
