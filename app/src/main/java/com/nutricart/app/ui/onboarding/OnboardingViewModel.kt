package com.nutricart.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Numbers shown on the final summary step. */
data class OnboardingPreview(
    val bmr: Int,
    val targets: DailyTargets,
    /** True when the computed target (TDEE + goal delta) fell below the safety floor and was clamped up to it. */
    val raisedToFloor: Boolean,
)

/**
 * Everything the onboarding screens display, in one immutable object.
 * Text inputs are kept as raw Strings; the validated numbers are derived below.
 */
data class OnboardingUiState(
    val step: Int = STEP_SEX,
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val underageBlocked: Boolean = false,
    val heightCmText: String = "",
    val weightKgText: String = "",
    val activityLevel: ActivityLevel? = null,
    val goal: Goal? = null,
    val targetKgPerWeek: Double = 0.0,
    val snacksPerDay: Int = 1,
    val isVegetarian: Boolean = false,
    val noPork: Boolean = false,
    val allergies: Set<Allergen> = emptySet(),
    val preview: OnboardingPreview? = null,
    val finished: Boolean = false,
) {
    /** null = empty or out of the sane range -> the field shows an error. */
    val heightCm: Int?
        get() = heightCmText.toIntOrNull()?.takeIf { it in ProfileOptions.HEIGHT_CM_RANGE }

    val weightKg: Double?
        get() = weightKgText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it in ProfileOptions.WEIGHT_KG_RANGE }

    /** Whether the Next button is enabled on the current step. */
    val canGoNext: Boolean
        get() = when (step) {
            STEP_SEX -> sex != null
            STEP_BIRTH -> birthDate != null && !underageBlocked
            STEP_BODY -> heightCm != null && weightKg != null
            STEP_ACTIVITY -> activityLevel != null
            STEP_GOAL -> goal != null && (goal == Goal.MAINTAIN || targetKgPerWeek > 0.0)
            STEP_DIET -> true // all diet options are optional
            else -> false     // the summary step has Start instead of Next
        }

    companion object {
        const val STEP_SEX = 0
        const val STEP_BIRTH = 1
        const val STEP_BODY = 2
        const val STEP_ACTIVITY = 3
        const val STEP_GOAL = 4
        const val STEP_DIET = 5
        const val STEP_SUMMARY = 6
        const val STEP_COUNT = 7
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectSex(sex: Sex) = _uiState.update { it.copy(sex = sex) }

    fun selectBirthDate(date: LocalDate) = _uiState.update { state ->
        state.copy(
            birthDate = date,
            // Hard rule from the spec: the app refuses to work for users under 18.
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

    fun next() = _uiState.update { state ->
        if (!state.canGoNext) return@update state
        val nextStep = state.step + 1
        if (nextStep == OnboardingUiState.STEP_SUMMARY) {
            // Entering the summary: compute the numbers once and keep them in state.
            state.copy(step = nextStep, preview = buildPreview(state))
        } else {
            state.copy(step = nextStep)
        }
    }

    fun back() = _uiState.update { state ->
        if (state.step == OnboardingUiState.STEP_SEX) state
        else state.copy(step = state.step - 1)
    }

    /** Saves everything; AppRoot switches to the main app when the flag flips. */
    fun finish() {
        val state = _uiState.value
        // Defense in depth: the under-18 rule is enforced again right before
        // saving, not only by the step navigation.
        if (state.underageBlocked) return
        if (state.step != OnboardingUiState.STEP_SUMMARY) return
        // All of these are guaranteed by canGoNext on earlier steps;
        // the early returns just make that explicit for the compiler.
        val sex = state.sex ?: return
        val birthDate = state.birthDate ?: return
        val heightCm = state.heightCm ?: return
        val weightKg = state.weightKg ?: return
        val level = state.activityLevel ?: return
        val goal = state.goal ?: return

        viewModelScope.launch {
            repository.completeOnboarding(
                profile = UserProfileEntity(
                    sex = sex,
                    birthDateEpochDay = birthDate.toEpochDay(),
                    heightCm = heightCm,
                    activityLevel = level,
                    goal = goal,
                    targetKgPerWeek = state.targetKgPerWeek,
                    snacksPerDay = state.snacksPerDay,
                    isVegetarian = state.isVegetarian,
                    noPork = state.noPork,
                    allergies = state.allergies.toList(),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
                weightKg = weightKg,
                todayEpochDay = LocalDate.now().toEpochDay(),
            )
            _uiState.update { it.copy(finished = true) }
        }
    }

    private fun buildPreview(state: OnboardingUiState): OnboardingPreview? {
        val sex = state.sex ?: return null
        val birthDate = state.birthDate ?: return null
        val heightCm = state.heightCm ?: return null
        val weightKg = state.weightKg ?: return null
        val level = state.activityLevel ?: return null
        val goal = state.goal ?: return null

        val age = CalorieCalculator.ageYears(birthDate, LocalDate.now())
        val bmr = CalorieCalculator.bmr(sex, weightKg, heightCm.toDouble(), age)
        val target = CalorieCalculator.baseTargetKcal(
            sex, weightKg, heightCm.toDouble(), age, level, goal, state.targetKgPerWeek,
        )
        val beforeFloor =
            CalorieCalculator.tdee(bmr, level) +
                CalorieCalculator.goalDeltaKcal(goal, state.targetKgPerWeek)

        return OnboardingPreview(
            bmr = bmr.roundToInt(),
            targets = CalorieCalculator.macroTargets(target, weightKg),
            raisedToFloor = beforeFloor < CalorieCalculator.safetyFloorKcal(sex),
        )
    }
}
