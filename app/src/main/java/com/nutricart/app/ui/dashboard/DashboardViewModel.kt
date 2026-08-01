package com.nutricart.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.health.HealthConnectManager
import com.nutricart.app.data.repository.ActivityRepository
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.SyncResult
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.model.DailyTargets
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
    val caloriesOut: Int = 0,
    val steps: Int? = null,
    val exerciseMinutes: Int? = null,
    val sleepMinutes: Int? = null,
    val avgHeartRateBpm: Int? = null,
    val weightKg: Double = 0.0,
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
    healthConnectManager: HealthConnectManager,
) : ViewModel() {

    /** The permission set the screen hands to the system permission dialog. */
    val healthPermissions: Set<String> = healthConnectManager.permissions

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

    // When the date rolls over, switch to observing the new day's rows —
    // both the watch activity and the food diary totals.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayData = todayFlow.flatMapLatest { day ->
        combine(
            activityRepository.observeDay(day.toEpochDay()),
            diaryRepository.observeDayTotals(day.toEpochDay()),
        ) { activity, eaten -> activity to eaten }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        todayFlow,
        profileRepository.observeProfile(),
        profileRepository.observeLatestWeight(),
        todayData,
        syncStatus,
    ) { today, profile, weight, (activity, eaten), sync ->
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

            val targetKcal = if (activeKcal != null) {
                CalorieCalculator.adjustedTargetKcal(
                    profile.sex, bmr, profile.goal, profile.targetKgPerWeek, activeKcal,
                )
            } else {
                CalorieCalculator.baseTargetKcal(
                    profile.sex, weight.weightKg, profile.heightCm.toDouble(), age,
                    profile.activityLevel, profile.goal, profile.targetKgPerWeek,
                )
            }
            val targets = CalorieCalculator.macroTargets(targetKcal, weight.weightKg)
            val eatenKcal = eaten.kcal.roundToInt()

            DashboardUiState(
                loading = false,
                targets = targets,
                adjustedByActivity = activeKcal != null,
                eatenKcal = eatenKcal,
                remainingKcal = targets.kcal - eatenKcal,
                eatenProteinG = eaten.proteinG.roundToInt(),
                eatenFatG = eaten.fatG.roundToInt(),
                eatenCarbsG = eaten.carbsG.roundToInt(),
                caloriesOut = CalorieCalculator
                    .caloriesOut(bmr, profile.activityLevel, activeKcal)
                    .roundToInt(),
                steps = activity?.steps,
                exerciseMinutes = activity?.exerciseMinutes,
                sleepMinutes = activity?.sleepMinutes,
                avgHeartRateBpm = activity?.avgHeartRateBpm,
                weightKg = weight.weightKg,
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
}
