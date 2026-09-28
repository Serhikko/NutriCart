package com.nutricart.app.widget

import com.nutricart.app.data.repository.ActivityRepository
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.WorkoutRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.logic.DailyTargetMath
import com.nutricart.app.domain.model.WorkoutSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** The snapshot the home-screen widget renders. */
data class WidgetData(
    val remainingKcal: Int,
    val eatenKcal: Int,
    val targetKcal: Int,
)

/**
 * One day's headline numbers, the same rule as the dashboard. Also what the
 * cloud sync publishes as a `day_summaries` row, so the website can never
 * disagree with the phone about the target.
 */
data class DayNumbers(
    val epochDay: Long,
    val targetKcal: Int,
    val eatenKcal: Int,
    /** Measured by the watch/phone; null = Health Connect had nothing for the day. */
    val activeKcal: Double?,
    val steps: Int?,
    /** Manually logged workouts only; watch sessions are inside activeKcal. */
    val manualWorkoutKcal: Double,
)

/**
 * Today's numbers for the widget — the SAME DailyTargetMath rule as the
 * dashboard, so the widget can never disagree with the app.
 * null = onboarding not finished yet.
 */
@Singleton
class WidgetDataSource @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val workoutRepository: WorkoutRepository,
    private val diaryRepository: DiaryRepository,
) {

    suspend fun today(): WidgetData? {
        val numbers = forDay(LocalDate.now().toEpochDay()) ?: return null
        return WidgetData(
            remainingKcal = numbers.targetKcal - numbers.eatenKcal,
            eatenKcal = numbers.eatenKcal,
            targetKcal = numbers.targetKcal,
        )
    }

    /** The headline numbers of any day; null = onboarding not finished yet. */
    suspend fun forDay(epochDay: Long): DayNumbers? {
        val profile = profileRepository.observeProfile().first() ?: return null
        val weight = profileRepository.observeLatestWeight().first() ?: return null
        val day = LocalDate.ofEpochDay(epochDay)

        val activity = activityRepository.observeDay(epochDay).first()
        val manualWorkoutKcal = workoutRepository.observeDay(epochDay).first()
            .filter { it.source == WorkoutSource.MANUAL }
            .sumOf { it.kcal ?: 0.0 }
        val eatenKcal = diaryRepository.observeDayTotals(epochDay).first().kcal

        val age = CalorieCalculator.ageYears(
            LocalDate.ofEpochDay(profile.birthDateEpochDay), day,
        )
        val targetKcal = DailyTargetMath.dayTargetKcal(
            profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
            profile.activityLevel, profile.goal, profile.targetKgPerWeek,
            profile.customKcalTarget,
            activity?.activeKcal,
            manualWorkoutKcal,
        ).roundToInt()

        return DayNumbers(
            epochDay = epochDay,
            targetKcal = targetKcal,
            eatenKcal = eatenKcal.roundToInt(),
            activeKcal = activity?.activeKcal,
            steps = activity?.steps,
            manualWorkoutKcal = manualWorkoutKcal,
        )
    }
}

/** Hilt door for non-Hilt entry points (the Glance widget). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataSource(): WidgetDataSource
}
