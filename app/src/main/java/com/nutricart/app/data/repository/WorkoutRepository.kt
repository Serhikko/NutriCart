package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.RecurringWorkoutDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.dao.WorkoutDao
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.data.local.entity.WorkoutEntryEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.WorkoutMath
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutSource
import com.nutricart.app.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manually logged workouts. The kcal snapshot is computed HERE (via WorkoutMath)
 * so every caller stores the same math. Watch sessions are imported separately
 * by ActivityRepository during sync.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val recurringDao: RecurringWorkoutDao,
    private val weightDao: WeightDao,
    private val settings: SettingsDataStore,
) {

    fun observeDay(epochDay: Long): Flow<List<WorkoutEntryEntity>> =
        workoutDao.observeDay(epochDay)

    /** Time-based workout, e.g. 20 minutes of treadmill walking. */
    suspend fun addDuration(epochDay: Long, type: WorkoutType, weightKg: Double, minutes: Int) {
        workoutDao.insert(
            manualEntry(
                epochDay = epochDay,
                type = type,
                minutes = minutes,
                reps = null,
                kcal = WorkoutMath.kcalForDuration(type, weightKg, minutes),
            )
        )
    }

    /** Repetition-based workout, e.g. 100 push-ups. */
    suspend fun addReps(epochDay: Long, type: WorkoutType, weightKg: Double, reps: Int) {
        workoutDao.insert(
            manualEntry(
                epochDay = epochDay,
                type = type,
                minutes = null,
                reps = reps,
                kcal = WorkoutMath.kcalForReps(type, weightKg, reps),
            )
        )
    }

    suspend fun deleteManual(id: Long) = workoutDao.deleteManual(id)

    // --- Recurring rules (v0.13) ---

    suspend fun recurringRules(): List<RecurringWorkoutEntity> = recurringDao.all()

    /** Adds a rule; if it covers TODAY it also materializes today's entry. */
    suspend fun addRecurring(rule: RecurringWorkoutEntity, today: LocalDate) {
        val id = recurringDao.insert(rule)
        if (today.dayOfWeek in rule.days) {
            weightDao.observeLatest().first()?.let { weight ->
                workoutDao.insertIgnore(
                    recurringEntry(rule.copy(id = id), today.toEpochDay(), weight.weightKg)
                )
            }
        }
    }

    /** Deleting a rule keeps its already-materialized entries as history. */
    suspend fun deleteRecurring(id: Long) = recurringDao.delete(id)

    /**
     * Turns today's matching rules into ordinary workout entries — ONCE per
     * calendar day (DataStore marker), so a deleted auto-entry stays deleted.
     * The unique (recurringId, epochDay) index + IGNORE catches races anyway.
     */
    suspend fun materializeRecurringForToday(today: LocalDate) {
        val epochDay = today.toEpochDay()
        if (settings.lastRecurringMaterializedDay.first() == epochDay) return
        // No weight yet (mid-onboarding) — retry silently on the next open.
        val weightKg = weightDao.observeLatest().first()?.weightKg ?: return
        recurringDao.all()
            .filter { today.dayOfWeek in it.days }
            .forEach { rule ->
                workoutDao.insertIgnore(recurringEntry(rule, epochDay, weightKg))
            }
        settings.setLastRecurringMaterializedDay(epochDay)
    }

    private fun recurringEntry(
        rule: RecurringWorkoutEntity,
        epochDay: Long,
        weightKg: Double,
    ): WorkoutEntryEntity {
        val kcal = when (rule.type.kind) {
            WorkoutKind.DURATION ->
                WorkoutMath.kcalForDuration(rule.type, weightKg, rule.minutes ?: 0)
            WorkoutKind.REPS ->
                WorkoutMath.kcalForReps(rule.type, weightKg, rule.reps ?: 0)
        }
        return WorkoutEntryEntity(
            epochDay = epochDay,
            source = WorkoutSource.MANUAL, // counts toward the target like any manual workout
            recurringId = rule.id,
            hcSessionId = null,
            hcExerciseType = null,
            type = rule.type,
            title = null,
            minutes = rule.minutes,
            reps = rule.reps,
            kcal = kcal,
            loggedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun manualEntry(
        epochDay: Long,
        type: WorkoutType,
        minutes: Int?,
        reps: Int?,
        kcal: Double,
    ) = WorkoutEntryEntity(
        epochDay = epochDay,
        source = WorkoutSource.MANUAL,
        hcSessionId = null,
        hcExerciseType = null,
        type = type,
        title = null,
        minutes = minutes,
        reps = reps,
        kcal = kcal,
        loggedAtEpochMillis = System.currentTimeMillis(),
    )
}
