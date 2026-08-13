package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.WorkoutDao
import com.nutricart.app.data.local.entity.WorkoutEntryEntity
import com.nutricart.app.domain.logic.WorkoutMath
import com.nutricart.app.domain.model.WorkoutSource
import com.nutricart.app.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
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
