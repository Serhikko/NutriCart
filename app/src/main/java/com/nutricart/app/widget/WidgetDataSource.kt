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
        val profile = profileRepository.observeProfile().first() ?: return null
        val weight = profileRepository.observeLatestWeight().first() ?: return null
        val today = LocalDate.now()
        val epochDay = today.toEpochDay()

        val activity = activityRepository.observeDay(epochDay).first()
        val manualWorkoutKcal = workoutRepository.observeDay(epochDay).first()
            .filter { it.source == WorkoutSource.MANUAL }
            .sumOf { it.kcal ?: 0.0 }
        val eatenKcal = diaryRepository.observeDayTotals(epochDay).first().kcal

        val age = CalorieCalculator.ageYears(
            LocalDate.ofEpochDay(profile.birthDateEpochDay), today,
        )
        val targetKcal = DailyTargetMath.dayTargetKcal(
            profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
            profile.activityLevel, profile.goal, profile.targetKgPerWeek,
            profile.customKcalTarget,
            activity?.activeKcal,
            manualWorkoutKcal,
        ).roundToInt()

        return WidgetData(
            remainingKcal = targetKcal - eatenKcal.roundToInt(),
            eatenKcal = eatenKcal.roundToInt(),
            targetKcal = targetKcal,
        )
    }
}

/** Hilt door for non-Hilt entry points (the Glance widget). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataSource(): WidgetDataSource
}
