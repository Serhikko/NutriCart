package com.nutricart.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
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

    /** Everything we ask the user to grant. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        // Lets the hourly worker read while the app is closed. OPTIONAL: without
        // it sync still happens whenever the app is opened or pulled-to-refresh.
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    /**
     * The subset sync cannot work without (background read is not in it).
     * v1 simplification: ALL data-type permissions are required — denying any
     * single type (e.g. only sleep) shows the permission banner. Fine for now,
     * because one aggregate request covers all types at once.
     */
    private val requiredPermissions: Set<String> =
        permissions - HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

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

    suspend fun hasRequiredPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)

    /**
     * Daily totals via the AGGREGATE API. Aggregation is important: if both the
     * phone and the watch recorded the same walk, aggregate() de-duplicates the
     * overlap — summing raw records ourselves would count it twice.
     */
    suspend fun readDay(day: LocalDate): DayActivitySummary {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()

        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                    SleepSessionRecord.SLEEP_DURATION_TOTAL,
                    HeartRateRecord.BPM_AVG,
                ),
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
