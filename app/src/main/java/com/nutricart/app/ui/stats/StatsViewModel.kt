package com.nutricart.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.StatsRepository
import com.nutricart.app.data.repository.TopSources
import com.nutricart.app.domain.logic.AdherenceCalculator
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.logic.DailyTargetMath
import com.nutricart.app.domain.logic.DietInsights
import com.nutricart.app.domain.logic.NutrientTargets
import com.nutricart.app.domain.model.DailyTargets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

enum class StatsRange(val days: Int) { WEEK(7), MONTH(30), NINETY(90) }

/** One chart bar: the day, what was eaten, and the adherence verdict. */
data class DayBar(
    val epochDay: Long,
    val eatenKcal: Double,
    val state: AdherenceCalculator.DayState,
)

data class StatsUiState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.WEEK,
    /** One bar per calendar day of the range, oldest first. */
    val bars: List<DayBar> = emptyList(),
    /** Average of the PER-DAY targets (they differ by day activity). */
    val avgTargetKcal: Int = 0,
    // Averages over days that actually have entries — honest numbers.
    val avgEatenKcal: Int = 0,
    val avgProteinG: Int = 0,
    val avgFatG: Int = 0,
    val avgCarbsG: Int = 0,
    val avgFiberG: Int = 0,
    /** Current macro targets, for the "average vs target" pairs. */
    val targets: DailyTargets? = null,
    val loggedDays: Int = 0,
    val goodDays: Int = 0,
    /** Average per LOGGED diary day — same denominator as the other averages. */
    val avgWaterMl: Int = 0,
    val topSources: TopSources? = null,
    /** Gaps and excesses of the period; empty + loggedDays > 0 = all good. */
    val insights: List<DietInsights.Insight> = emptyList(),
    /** Which month the adherence calendar shows. */
    val calendarMonth: YearMonth = YearMonth.now(),
    /** Verdict per epochDay of that month (future days are absent). */
    val calendarStates: Map<Long, AdherenceCalculator.DayState> = emptyMap(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun selectRange(range: StatsRange) {
        _uiState.update { it.copy(range = range) }
        refresh()
    }

    fun previousMonth() {
        _uiState.update { it.copy(calendarMonth = it.calendarMonth.minusMonths(1)) }
        refresh()
    }

    /** Clamped at the current month — the future has nothing to show. */
    fun nextMonth() {
        _uiState.update { state ->
            if (state.calendarMonth < YearMonth.now()) {
                state.copy(calendarMonth = state.calendarMonth.plusMonths(1))
            } else {
                state
            }
        }
        refresh()
    }

    /** Called on screen resume and on every range change. */
    fun refresh() {
        // A slow old range-load must never overwrite a newer one.
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val profile = profileRepository.observeProfile().filterNotNull().first()
        val weight = profileRepository.observeLatestWeight().filterNotNull().first()
        val today = LocalDate.now().toEpochDay()
        val range = _uiState.value.range
        val from = today - (range.days - 1)

        val eatenByDay = statsRepository.eatenKcalByDay(from, today)
        val activityByDay = statsRepository.activityByDay(from, today)
        val workoutsByDay = statsRepository.manualWorkoutKcalByDay(from, today)
        val totals = statsRepository.rangeTotals(from, today)
        val water = statsRepository.waterStats(from, today)
        val topSources = statsRepository.topSources(from, today)

        val age = CalorieCalculator.ageYears(
            LocalDate.ofEpochDay(profile.birthDateEpochDay),
            LocalDate.now(),
        )

        // Historical targets use the CURRENT profile and weight (approved
        // approximation — old targets were never stored) but each day's OWN
        // activity: its watch kcal and its manual workouts.
        var targetSum = 0.0
        val bars = (from..today).map { day ->
            val dayTarget = DailyTargetMath.dayTargetKcal(
                profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
                profile.activityLevel, profile.goal, profile.targetKgPerWeek,
                profile.customKcalTarget,
                activityByDay[day]?.activeKcal,
                workoutsByDay[day] ?: 0.0,
            )
            targetSum += dayTarget
            val eaten = eatenByDay[day]
            DayBar(day, eaten ?: 0.0, AdherenceCalculator.dayState(eaten, dayTarget))
        }

        // Macro reference for the "average vs target" rows: the user's custom
        // numbers, or macros derived from the no-activity reference target.
        val referenceKcal = DailyTargetMath.referenceKcal(
            profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
            profile.activityLevel, profile.goal, profile.targetKgPerWeek,
            profile.customKcalTarget,
        )
        val targets = if (
            profile.customKcalTarget != null && profile.customProteinG != null &&
            profile.customFatG != null && profile.customCarbsG != null
        ) {
            DailyTargets(
                kcal = profile.customKcalTarget,
                proteinG = profile.customProteinG,
                fatG = profile.customFatG,
                carbsG = profile.customCarbsG,
            )
        } else {
            CalorieCalculator.macroTargets(referenceKcal, weight.weightKg)
        }

        // The calendar month is independent of the range chips and needs its
        // own queries (it can lie outside the range window).
        val month = _uiState.value.calendarMonth
        val monthFrom = month.atDay(1).toEpochDay()
        val monthTo = minOf(month.atEndOfMonth().toEpochDay(), today)
        val calendarStates = if (monthFrom > today) {
            emptyMap()
        } else {
            val monthEaten = statsRepository.eatenKcalByDay(monthFrom, monthTo)
            val monthActivity = statsRepository.activityByDay(monthFrom, monthTo)
            val monthWorkouts = statsRepository.manualWorkoutKcalByDay(monthFrom, monthTo)
            (monthFrom..monthTo).associateWith { day ->
                val dayTarget = DailyTargetMath.dayTargetKcal(
                    profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
                    profile.activityLevel, profile.goal, profile.targetKgPerWeek,
                    profile.customKcalTarget,
                    monthActivity[day]?.activeKcal,
                    monthWorkouts[day] ?: 0.0,
                )
                AdherenceCalculator.dayState(monthEaten[day], dayTarget)
            }
        }

        val loggedDays = totals.loggedDays
        // rawAvg feeds comparisons (rounding first would swallow overshoots
        // like 5.4 g of salt vs the 5 g limit); avg is for display only.
        fun rawAvg(sum: Double): Double = if (loggedDays > 0) sum / loggedDays else 0.0
        fun avg(sum: Double): Int = rawAvg(sum).roundToInt()

        // Gaps/excesses measured against the stable reference target (the
        // nutrient guides the dashboard shows on a plain no-activity day).
        val referenceKcalInt = targets.kcal
        val insights = if (loggedDays == 0) emptyList() else DietInsights.compute(
            avgProteinG = rawAvg(totals.proteinG),
            proteinTargetG = targets.proteinG,
            avgFiberG = rawAvg(totals.fiberG),
            fiberTargetG = NutrientTargets.fiberTargetG(referenceKcalInt),
            avgSugarsG = rawAvg(totals.sugarsG),
            sugarLimitG = NutrientTargets.sugarLimitG(referenceKcalInt),
            avgSaltG = rawAvg(totals.saltG),
            saltLimitG = NutrientTargets.SALT_LIMIT_G.roundToInt(),
            avgSatFatG = rawAvg(totals.saturatedFatG),
            satFatLimitG = NutrientTargets.saturatedFatLimitG(referenceKcalInt),
        )

        _uiState.update {
            it.copy(
                loading = false,
                bars = bars,
                calendarStates = calendarStates,
                insights = insights,
                avgTargetKcal = (targetSum / bars.size).roundToInt(),
                avgEatenKcal = avg(totals.kcal),
                avgProteinG = avg(totals.proteinG),
                avgFatG = avg(totals.fatG),
                avgCarbsG = avg(totals.carbsG),
                avgFiberG = avg(totals.fiberG),
                targets = targets,
                loggedDays = loggedDays,
                goodDays = bars.count { bar -> bar.state == AdherenceCalculator.DayState.GOOD },
                // Same denominator as every other row of the card: LOGGED
                // diary days — 2 sporadic +water taps must not read as a
                // daily habit (review-caught).
                avgWaterMl = if (loggedDays > 0) {
                    (water.totalMl.toDouble() / loggedDays).roundToInt()
                } else {
                    0
                },
                topSources = topSources,
            )
        }
    }
}
