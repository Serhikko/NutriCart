package com.nutricart.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The editable settings form. Unlike onboarding, all fields start from the
 * SAVED profile, so they are non-null; loading=true only while we read it.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val sex: Sex = Sex.MALE,
    val birthDate: LocalDate = LocalDate.of(2000, 1, 1),
    val underageBlocked: Boolean = false,
    val heightCmText: String = "",
    val weightKgText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val targetKgPerWeek: Double = 0.0,
    val snacksPerDay: Int = 1,
    val isVegetarian: Boolean = false,
    val noPork: Boolean = false,
    val allergies: Set<Allergen> = emptySet(),
    // Kept from the loaded profile so saving does not change the creation date.
    val createdAtEpochMillis: Long = 0L,
    // The weight the form was opened with — used to detect a real edit.
    val initialWeightKg: Double? = null,
    val saved: Boolean = false,
    val showResetDialog: Boolean = false,
) {
    /** null = empty or out of the sane range -> the field shows an error. */
    val heightCm: Int?
        get() = heightCmText.toIntOrNull()?.takeIf { it in ProfileOptions.HEIGHT_CM_RANGE }

    val weightKg: Double?
        get() = weightKgText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it in ProfileOptions.WEIGHT_KG_RANGE }

    val canSave: Boolean
        get() = !loading && !underageBlocked && heightCm != null && weightKg != null &&
            (goal == Goal.MAINTAIN || targetKgPerWeek > 0.0)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load the saved profile and latest weight once into the editable form.
        viewModelScope.launch {
            val profile = repository.observeProfile().filterNotNull().first()
            val weight = repository.observeLatestWeight().filterNotNull().first()
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    sex = profile.sex,
                    birthDate = LocalDate.ofEpochDay(profile.birthDateEpochDay),
                    heightCmText = profile.heightCm.toString(),
                    weightKgText = weight.weightKg.toString(),
                    activityLevel = profile.activityLevel,
                    goal = profile.goal,
                    targetKgPerWeek = profile.targetKgPerWeek,
                    snacksPerDay = profile.snacksPerDay,
                    isVegetarian = profile.isVegetarian,
                    noPork = profile.noPork,
                    allergies = profile.allergies.toSet(),
                    createdAtEpochMillis = profile.createdAtEpochMillis,
                    initialWeightKg = weight.weightKg,
                )
            }
        }
    }

    fun selectSex(sex: Sex) = _uiState.update { it.copy(sex = sex) }

    fun selectBirthDate(date: LocalDate) = _uiState.update { state ->
        state.copy(
            birthDate = date,
            // The 18+ rule applies when editing too, not only in onboarding.
            underageBlocked = !CalorieCalculator.isAdult(date, LocalDate.now()),
        )
    }

    fun setHeightText(text: String) = _uiState.update { it.copy(heightCmText = text) }

    fun setWeightText(text: String) = _uiState.update { it.copy(weightKgText = text) }

    fun selectActivityLevel(level: ActivityLevel) =
        _uiState.update { it.copy(activityLevel = level) }

    fun selectGoal(goal: Goal) = _uiState.update { state ->
        val rate = when {
            goal == Goal.MAINTAIN -> 0.0
            state.targetKgPerWeek > 0.0 -> state.targetKgPerWeek
            else -> ProfileOptions.DEFAULT_RATE_KG_PER_WEEK
        }
        state.copy(goal = goal, targetKgPerWeek = rate)
    }

    fun selectRate(rate: Double) = _uiState.update { it.copy(targetKgPerWeek = rate) }

    fun selectSnacksPerDay(count: Int) = _uiState.update { it.copy(snacksPerDay = count) }

    fun toggleVegetarian(enabled: Boolean) = _uiState.update { it.copy(isVegetarian = enabled) }

    fun toggleNoPork(enabled: Boolean) = _uiState.update { it.copy(noPork = enabled) }

    fun toggleAllergen(allergen: Allergen) = _uiState.update { state ->
        val newSet =
            if (allergen in state.allergies) state.allergies - allergen
            else state.allergies + allergen
        state.copy(allergies = newSet)
    }

    fun setShowResetDialog(show: Boolean) = _uiState.update { it.copy(showResetDialog = show) }

    /** Saves the profile and today's weight; the screen navigates back on `saved`. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val heightCm = state.heightCm ?: return
        val weightKg = state.weightKg ?: return

        viewModelScope.launch {
            repository.updateProfile(
                UserProfileEntity(
                    sex = state.sex,
                    birthDateEpochDay = state.birthDate.toEpochDay(),
                    heightCm = heightCm,
                    activityLevel = state.activityLevel,
                    goal = state.goal,
                    targetKgPerWeek = state.targetKgPerWeek,
                    snacksPerDay = state.snacksPerDay,
                    isVegetarian = state.isVegetarian,
                    noPork = state.noPork,
                    allergies = state.allergies.toList(),
                    createdAtEpochMillis = state.createdAtEpochMillis,
                )
            )
            // Only log a weight entry when the user actually changed the value.
            // Otherwise saving unrelated settings would record the stale pre-filled
            // weight as a fresh weigh-in for today (and, being MANUAL, it would
            // permanently shadow a same-day watch measurement).
            if (weightKg != state.initialWeightKg) {
                repository.logWeight(weightKg, LocalDate.now().toEpochDay())
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    /** Wipes everything; AppRoot then automatically returns to onboarding. */
    fun confirmReset() {
        // Close the dialog immediately: it must not stay tappable while the
        // reset runs (double-confirm would launch the reset twice).
        _uiState.update { it.copy(showResetDialog = false) }
        viewModelScope.launch {
            repository.resetAll()
        }
    }
}
