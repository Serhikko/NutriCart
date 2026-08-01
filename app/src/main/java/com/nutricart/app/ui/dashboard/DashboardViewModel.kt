package com.nutricart.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.model.DailyTargets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(
        val targets: DailyTargets,
        val weightKg: Double,
    ) : DashboardUiState
}

/**
 * Placeholder dashboard for this build step: reads the saved profile + latest
 * weight and recomputes today's targets. Steps/water/food arrive in later steps.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeProfile(),
        repository.observeLatestWeight(),
    ) { profile, weight ->
        if (profile == null || weight == null) {
            DashboardUiState.Loading
        } else {
            val today = LocalDate.now()
            val age = CalorieCalculator.ageYears(
                LocalDate.ofEpochDay(profile.birthDateEpochDay),
                today,
            )
            val kcalTarget = CalorieCalculator.baseTargetKcal(
                sex = profile.sex,
                weightKg = weight.weightKg,
                heightCm = profile.heightCm.toDouble(),
                ageYears = age,
                level = profile.activityLevel,
                goal = profile.goal,
                targetKgPerWeek = profile.targetKgPerWeek,
            )
            DashboardUiState.Ready(
                targets = CalorieCalculator.macroTargets(kcalTarget, weight.weightKg),
                weightKg = weight.weightKg,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState.Loading,
    )
}
