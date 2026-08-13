package com.nutricart.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.health.HealthConnectManager
import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.entity.DailyActivityEntity
import com.nutricart.app.data.local.entity.PlannedMealEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.local.entity.WorkoutEntryEntity
import com.nutricart.app.data.repository.ActivityRepository
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.PlanRepository
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.SyncResult
import com.nutricart.app.data.repository.WaterRepository
import com.nutricart.app.data.repository.WorkoutRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.logic.NutrientTargets
import com.nutricart.app.domain.logic.StreakCalculator
import com.nutricart.app.domain.logic.WeightTrendCalculator
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.RecipeNutrition
import com.nutricart.app.domain.model.WeightSource
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutSource
import com.nutricart.app.domain.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Which Health Connect problem (if any) the dashboard should explain. */
enum class HcBannerState { NONE, NOT_INSTALLED, UPDATE_REQUIRED, NO_PERMISSION }

/** One line of the "today's menu" card (a planned meal, scaled). */
data class TodayMenuItem(
    val recipeId: Long,
    val slot: MealSlot,
    val name: String,
    val kcal: Int,
    val portionFactor: Double,
)

/** One row of the workout list on the activity card. */
data class WorkoutItem(
    val id: Long,
    /** Watch sessions are display-only; manual entries can be deleted. */
    val isFromWatch: Boolean,
    /** Catalog type — set for manual entries only. */
    val type: WorkoutType?,
    /** Raw HC exercise-type constant — set for watch sessions only. */
    val hcExerciseType: Int?,
    /** Session name from the watch app, may be null even for watch rows. */
    val title: String?,
    val minutes: Int?,
    val reps: Int?,
    /** null = the watch session carried no calorie data. */
    val kcal: Int?,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val targets: DailyTargets? = null,
    /** True when today's target already includes MEASURED watch activity. */
    val adjustedByActivity: Boolean = false,
    val eatenKcal: Int = 0,
    val remainingKcal: Int = 0,
    val eatenProteinG: Int = 0,
    val eatenFatG: Int = 0,
    val eatenCarbsG: Int = 0,
    /** Detail nutrients: sums of the KNOWN parts of today's entries. */
    val eatenFiberG: Double = 0.0,
    val eatenSugarsG: Double = 0.0,
    val eatenSaltG: Double = 0.0,
    val eatenSatFatG: Double = 0.0,
    /** Daily guides derived from today's kcal target (see NutrientTargets). */
    val fiberTargetG: Int = 0,
    val sugarLimitG: Int = 0,
    val saltLimitG: Int = 0,
    val satFatLimitG: Int = 0,
    val caloriesOut: Int = 0,
    /** kcal ADDED to today's target by activity: watch active kcal + manual workouts. */
    val activityBonusKcal: Int = 0,
    val steps: Int? = null,
    /** Watch active calories for today (raw, for the activity card). */
    val activeKcal: Int? = null,
    val workouts: List<WorkoutItem> = emptyList(),
    val exerciseMinutes: Int? = null,
    val sleepMinutes: Int? = null,
    val avgHeartRateBpm: Int? = null,
    val weightKg: Double = 0.0,
    val waterMl: Int = 0,
    /** Per-day weight points (MANUAL wins over watch), oldest first, max 30. */
    val weightPoints: List<Pair<Long, Double>> = emptyList(),
    val weightTrend: WeightTrendCalculator.Trend? = null,
    /** Days in a row with at least one diary entry. */
    val streakDays: Int = 0,
    /** Today's planned meals; empty = no plan for today. */
    val todayMenu: List<TodayMenuItem> = emptyList(),
    /** Sync works, but Health Connect holds no activity data — probably the
     *  watch app (e.g. Samsung Health) is not connected to Health Connect. */
    val showNoDataHint: Boolean = false,
    val hcBanner: HcBannerState = HcBannerState.NONE,
    val refreshing: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    /** One-shot flag: the last refresh failed; the screen shows a snackbar and clears it. */
    val syncFailed: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    diaryRepository: DiaryRepository,
    private val waterRepository: WaterRepository,
    private val workoutRepository: WorkoutRepository,
    planRepository: PlanRepository,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    // All recipes by id — the today's-menu card needs their names AND kcal
    // (first access also seeds the recipe tables).
    private val recipesById = MutableStateFlow<Map<Long, RecipeNutrition>>(emptyMap())

    init {
        viewModelScope.launch { recipesById.value = planRepository.nutritionById() }
    }

    /** The permission set the screen hands to the system permission dialog. */
    val healthPermissions: Set<String>
        get() = healthConnectManager.permissionsToRequest()

    /** True when the dialog result contains BOTH permissions sync needs. */
    fun hasRequiredHealthPermissions(granted: Set<String>): Boolean =
        healthConnectManager.hasRequiredPermissions(granted)

    private val hcBanner = MutableStateFlow(HcBannerState.NONE)
    private val refreshing = MutableStateFlow(false)
    private val syncFailed = MutableStateFlow(false)

    // Which date "today" means. refresh() re-reads the clock, so after midnight
    // the next refresh (screen resume or pull) rolls the dashboard to the new day.
    private val todayFlow = MutableStateFlow(LocalDate.now())

    // Everything about the sync itself, grouped so the main combine stays small.
    private data class SyncStatus(
        val banner: HcBannerState,
        val refreshing: Boolean,
        val lastSyncEpochMillis: Long?,
        val syncFailed: Boolean,
    )

    private val syncStatus = combine(
        hcBanner, refreshing, activityRepository.observeLastSync(), syncFailed,
    ) { banner, isRefreshing, lastSync, failed ->
        SyncStatus(banner, isRefreshing, lastSync, failed)
    }

    private data class TodayData(
        val activity: DailyActivityEntity?,
        val eaten: DayNutritionTotals,
        val waterMl: Int,
        val planMeals: List<PlannedMealEntity>,
        val workouts: List<WorkoutEntryEntity>,
    )

    // When the date rolls over, switch to observing the new day's rows —
    // watch activity, food diary totals, water, the planned menu and workouts.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayData = todayFlow.flatMapLatest { day ->
        combine(
            activityRepository.observeDay(day.toEpochDay()),
            diaryRepository.observeDayTotals(day.toEpochDay()),
            waterRepository.observeDayTotal(day.toEpochDay()),
            planRepository.observeDay(day.toEpochDay()),
            workoutRepository.observeDay(day.toEpochDay()),
        ) { activity, eaten, water, planMeals, workouts ->
            TodayData(activity, eaten, water, planMeals, workouts)
        }
    }

    // Everything about the user, grouped so the main combine stays small.
    private data class ProfileData(
        val profile: UserProfileEntity?,
        val latestWeight: WeightEntryEntity?,
        val history: List<WeightEntryEntity>,
        val loggedDays: List<Long>,
    )

    private val profileData = combine(
        profileRepository.observeProfile(),
        profileRepository.observeLatestWeight(),
        profileRepository.observeWeightHistory(),
        diaryRepository.observeLoggedDays(),
    ) { profile, latest, history, loggedDays -> ProfileData(profile, latest, history, loggedDays) }

    val uiState: StateFlow<DashboardUiState> = combine(
        todayFlow,
        profileData,
        todayData,
        recipesById,
        syncStatus,
    ) { today, pd, todayValues, recipes, sync ->
        val (activity, eaten, waterMl, planMeals) = todayValues
        val profile = pd.profile
        val weight = pd.latestWeight
        if (profile == null || weight == null) {
            DashboardUiState(
                loading = true,
                hcBanner = sync.banner,
                refreshing = sync.refreshing,
                syncFailed = sync.syncFailed,
            )
        } else {
            val age = CalorieCalculator.ageYears(
                LocalDate.ofEpochDay(profile.birthDateEpochDay),
                today,
            )
            val bmr = CalorieCalculator.bmr(
                profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
            )
            // null = the watch reported nothing today; a measured 0.0 is different
            // and DOES switch the formula (see CalorieCalculator for the reason).
            val activeKcal = activity?.activeKcal

            // Manually logged workouts ALWAYS raise the day's budget: the watch
            // never saw them, so they can't be double-counted with activeKcal.
            // (Watch sessions are already inside activeKcal — never added here.)
            val manualWorkoutKcal = todayValues.workouts
                .filter { it.source == WorkoutSource.MANUAL }
                .sumOf { it.kcal ?: 0.0 }

            // Manual override: the user's number replaces BOTH formulas —
            // the watch no longer switches anything, only manual workouts add.
            val customKcal = profile.customKcalTarget
            val baseTarget = customKcal?.toDouble() ?: CalorieCalculator.baseTargetKcal(
                profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
                profile.activityLevel, profile.goal, profile.targetKgPerWeek,
            )
            val targetKcal = manualWorkoutKcal + if (customKcal == null && activeKcal != null) {
                CalorieCalculator.adjustedTargetKcal(
                    profile.sex, bmr, profile.goal, profile.targetKgPerWeek, activeKcal,
                )
            } else {
                baseTarget
            }
            val targets = if (
                customKcal != null && profile.customProteinG != null &&
                profile.customFatG != null && profile.customCarbsG != null
            ) {
                DailyTargets(
                    kcal = targetKcal.roundToInt(),
                    proteinG = profile.customProteinG,
                    fatG = profile.customFatG,
                    carbsG = profile.customCarbsG,
                )
            } else {
                CalorieCalculator.macroTargets(targetKcal, weight.weightKg)
            }
            val eatenKcal = eaten.kcal.roundToInt()
            val weightPoints = dedupePerDay(pd.history)

            DashboardUiState(
                loading = false,
                targets = targets,
                adjustedByActivity = customKcal == null && activeKcal != null,
                eatenKcal = eatenKcal,
                remainingKcal = targets.kcal - eatenKcal,
                eatenProteinG = eaten.proteinG.roundToInt(),
                eatenFatG = eaten.fatG.roundToInt(),
                eatenCarbsG = eaten.carbsG.roundToInt(),
                eatenFiberG = eaten.fiberG,
                eatenSugarsG = eaten.sugarsG,
                eatenSaltG = eaten.saltG,
                eatenSatFatG = eaten.saturatedFatG,
                fiberTargetG = NutrientTargets.fiberTargetG(targets.kcal),
                sugarLimitG = NutrientTargets.sugarLimitG(targets.kcal),
                saltLimitG = NutrientTargets.SALT_LIMIT_G.roundToInt(),
                satFatLimitG = NutrientTargets.saturatedFatLimitG(targets.kcal),
                caloriesOut = (CalorieCalculator
                    .caloriesOut(bmr, profile.activityLevel, activeKcal) + manualWorkoutKcal)
                    .roundToInt(),
                // The REAL difference vs the questionnaire-only target — NOT
                // raw activeKcal + manual: the safety floor can absorb part of
                // the raise, and on a watch day the formula switch itself can
                // even LOWER the target. When this is <= 0 the screen shows the
                // neutral "adjusted for watch activity" note instead.
                activityBonusKcal = (targetKcal - baseTarget).roundToInt(),
                steps = activity?.steps,
                activeKcal = activeKcal?.roundToInt(),
                workouts = todayValues.workouts.map { w ->
                    WorkoutItem(
                        id = w.id,
                        isFromWatch = w.source == WorkoutSource.HEALTH_CONNECT,
                        type = w.type,
                        hcExerciseType = w.hcExerciseType,
                        title = w.title,
                        minutes = w.minutes,
                        reps = w.reps,
                        kcal = w.kcal?.roundToInt(),
                    )
                },
                exerciseMinutes = activity?.exerciseMinutes,
                sleepMinutes = activity?.sleepMinutes,
                avgHeartRateBpm = activity?.avgHeartRateBpm,
                weightKg = weight.weightKg,
                waterMl = waterMl,
                weightPoints = weightPoints,
                weightTrend = WeightTrendCalculator.calculate(weightPoints),
                streakDays = StreakCalculator.calculate(
                    pd.loggedDays.toSet(),
                    today.toEpochDay(),
                ),
                todayMenu = planMeals
                    .sortedWith(compareBy({ it.slot.ordinal }, { it.position }))
                    .mapNotNull { row ->
                        recipes[row.recipeId]?.let { n ->
                            TodayMenuItem(
                                recipeId = row.recipeId,
                                slot = row.slot,
                                name = n.name,
                                kcal = (n.kcal * row.portionFactor).roundToInt(),
                                portionFactor = row.portionFactor,
                            )
                        }
                    },
                // A sync HAS run (the day row exists only after one), everything
                // is set up, yet both core values are absent -> the data source
                // (watch app) is likely not feeding Health Connect. No
                // "refreshing" clause: it would collapse and re-expand the hint
                // card on every resume, jumping the whole layout.
                showNoDataHint = sync.banner == HcBannerState.NONE &&
                    activity != null && activity.steps == null && activity.activeKcal == null,
                hcBanner = sync.banner,
                refreshing = sync.refreshing,
                lastSyncEpochMillis = sync.lastSyncEpochMillis,
                syncFailed = sync.syncFailed,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    // No sync in init: the screen calls refresh() on every resume,
    // which covers the first open too.

    /** Screen resume, pull-to-refresh and "permissions granted" all land here. */
    fun refresh() {
        viewModelScope.launch {
            todayFlow.value = LocalDate.now() // roll the date if midnight passed
            refreshing.value = true
            when (activityRepository.syncNow()) {
                SyncResult.SUCCESS -> hcBanner.value = HcBannerState.NONE
                SyncResult.NOT_INSTALLED -> hcBanner.value = HcBannerState.NOT_INSTALLED
                SyncResult.UPDATE_REQUIRED -> hcBanner.value = HcBannerState.UPDATE_REQUIRED
                SyncResult.NO_PERMISSION -> hcBanner.value = HcBannerState.NO_PERMISSION
                // Transient failure: keep the current banner, tell the user once.
                SyncResult.ERROR -> syncFailed.value = true
            }
            refreshing.value = false
        }
    }

    fun clearSyncFailed() {
        syncFailed.value = false
    }

    /**
     * Logs a manual workout for today. `amount` is minutes for DURATION types
     * and repetitions for REPS types — the catalog decides which formula runs.
     */
    fun addWorkout(type: WorkoutType, amount: Int) {
        viewModelScope.launch {
            // The dialog is only reachable after the state has loaded, so the
            // weight is real; the guard is just a belt against a stale 0.
            val weightKg = uiState.value.weightKg
            if (weightKg <= 0.0 || amount <= 0) return@launch
            val day = todayFlow.value.toEpochDay()
            when (type.kind) {
                WorkoutKind.DURATION ->
                    workoutRepository.addDuration(day, type, weightKg, amount)
                WorkoutKind.REPS ->
                    workoutRepository.addReps(day, type, weightKg, amount)
            }
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { workoutRepository.deleteManual(id) }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { waterRepository.add(todayFlow.value.toEpochDay(), ml) }
    }

    fun undoWater() {
        viewModelScope.launch { waterRepository.undoLast(todayFlow.value.toEpochDay()) }
    }

    /** One weight point per day, MANUAL beating HEALTH_CONNECT; last 30 days. */
    private fun dedupePerDay(entries: List<WeightEntryEntity>): List<Pair<Long, Double>> =
        entries
            .groupBy { it.epochDay }
            .map { (day, rows) ->
                val chosen = rows.firstOrNull { it.source == WeightSource.MANUAL } ?: rows.first()
                day to chosen.weightKg
            }
            .sortedBy { it.first }
            .takeLast(30)
}
