package com.nutricart.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.health.HcAvailability
import com.nutricart.app.data.health.HealthConnectManager
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.WorkoutRepository
import com.nutricart.app.data.settings.SecretsDataStore
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.reminders.MealReminderScheduling
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import java.time.DayOfWeek
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
    val cookingSessionsPerWeek: Int = ProfileOptions.DEFAULT_COOKING_SESSIONS,
    val isVegetarian: Boolean = false,
    val noPork: Boolean = false,
    val allergies: Set<Allergen> = emptySet(),
    /** Standing workout rules shown in the "Regular activities" section. */
    val recurring: List<RecurringWorkoutEntity> = emptyList(),
    val showRecurringDialog: Boolean = false,
    /** Per-meal reminder rows (slot order). */
    val reminders: List<ReminderUi> = emptyList(),
    /** Which slot's time picker is open; null = none. */
    val editingReminderSlot: MealSlot? = null,
    /** Manual daily targets: when ON, all four fields below must be valid. */
    val manualTargets: Boolean = false,
    val customKcalText: String = "",
    val customProteinText: String = "",
    val customFatText: String = "",
    val customCarbsText: String = "",
    // Kept from the loaded profile so saving does not change the creation date.
    val createdAtEpochMillis: Long = 0L,
    // The weight the form was opened with — used to detect a real edit.
    val initialWeightKg: Double? = null,
    /** Health Connect diagnostics, moved here off the dashboard. */
    val hcAvailable: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    /** The optional AI assistant's key, as typed. */
    val aiKeyText: String = "",
    val aiKeyStored: Boolean = false,
    val saved: Boolean = false,
    val showResetDialog: Boolean = false,
) {
    /** null = empty or out of the sane range -> the field shows an error. */
    val heightCm: Int?
        get() = heightCmText.toIntOrNull()?.takeIf { it in ProfileOptions.HEIGHT_CM_RANGE }

    val weightKg: Double?
        get() = weightKgText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it in ProfileOptions.WEIGHT_KG_RANGE }

    // Manual-target fields: null = empty or outside the sane range.
    // The kcal floor is the APPROVED safety rule (1500 male / 1200 female):
    // manual targets replace the formulas, but never the safety floor.
    val customKcal: Int?
        get() = customKcalText.toIntOrNull()
            ?.takeIf { it in CalorieCalculator.safetyFloorKcal(sex).toInt()..6000 }
    val customProtein: Int?
        get() = customProteinText.toIntOrNull()?.takeIf { it in 10..400 }
    val customFat: Int?
        get() = customFatText.toIntOrNull()?.takeIf { it in 10..300 }
    val customCarbs: Int?
        get() = customCarbsText.toIntOrNull()?.takeIf { it in 0..800 }

    val customTargetsOk: Boolean
        get() = !manualTargets || (customKcal != null && customProtein != null &&
            customFat != null && customCarbs != null)

    val canSave: Boolean
        get() = !loading && !underageBlocked && heightCm != null && weightKg != null &&
            (goal == Goal.MAINTAIN || targetKgPerWeek > 0.0) && customTargetsOk

    /**
     * A pasted key often carries a trailing newline, and OkHttp rejects an
     * illegal header VALUE with an exception whose message contains the key
     * itself. Catching it here means it never reaches the network layer.
     */
    val aiKeyValid: Boolean
        get() = aiKeyText.trim().let { key ->
            key.isNotEmpty() && key.all { it in ' '..'~' }
        }
}

/** One reminder row: the meal, on/off, and the time in minutes from midnight. */
data class ReminderUi(
    val slot: MealSlot,
    val enabled: Boolean,
    val minutesOfDay: Int,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val settings: SettingsDataStore,
    private val secrets: SecretsDataStore,
    healthConnectManager: HealthConnectManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Health Connect diagnostics live here now. The "open Health Connect
        // settings" intent resolves nowhere when the app is missing, so the
        // whole section only exists when Health Connect is really installed.
        _uiState.update {
            it.copy(hcAvailable = healthConnectManager.availability() == HcAvailability.AVAILABLE)
        }
        viewModelScope.launch {
            settings.lastHcSyncEpochMillis.collect { millis ->
                _uiState.update { it.copy(lastSyncEpochMillis = millis) }
            }
        }
        // Read ONCE, like the profile: a live collector would overwrite the
        // field while the user is typing into it.
        viewModelScope.launch {
            val stored = secrets.aiApiKey.first()
            _uiState.update {
                it.copy(aiKeyText = stored.orEmpty(), aiKeyStored = !stored.isNullOrBlank())
            }
        }

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
                    cookingSessionsPerWeek = profile.cookingSessionsPerWeek,
                    isVegetarian = profile.isVegetarian,
                    noPork = profile.noPork,
                    allergies = profile.allergies.toSet(),
                    manualTargets = profile.customKcalTarget != null,
                    customKcalText = profile.customKcalTarget?.toString() ?: "",
                    customProteinText = profile.customProteinG?.toString() ?: "",
                    customFatText = profile.customFatG?.toString() ?: "",
                    customCarbsText = profile.customCarbsG?.toString() ?: "",
                    createdAtEpochMillis = profile.createdAtEpochMillis,
                    initialWeightKg = weight.weightKg,
                )
            }
            refreshRecurring()
            refreshReminders()
        }
    }

    private suspend fun refreshReminders() {
        val rows = MealSlot.entries.map { slot ->
            ReminderUi(
                slot = slot,
                enabled = settings.reminderEnabled(slot).first(),
                minutesOfDay = settings.reminderMinutes(slot).first(),
            )
        }
        _uiState.update { it.copy(reminders = rows) }
    }

    fun toggleReminder(slot: MealSlot, enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(slot, enabled)
            if (enabled) {
                MealReminderScheduling.schedule(
                    appContext, slot, settings.reminderMinutes(slot).first(),
                )
            } else {
                MealReminderScheduling.cancel(appContext, slot)
            }
            refreshReminders()
        }
    }

    fun startEditingReminder(slot: MealSlot) =
        _uiState.update { it.copy(editingReminderSlot = slot) }

    fun cancelEditingReminder() =
        _uiState.update { it.copy(editingReminderSlot = null) }

    fun setReminderTime(minutesOfDay: Int) {
        val slot = _uiState.value.editingReminderSlot ?: return
        _uiState.update { it.copy(editingReminderSlot = null) }
        viewModelScope.launch {
            settings.setReminderMinutes(slot, minutesOfDay)
            // A live reminder follows the new time immediately.
            if (settings.reminderEnabled(slot).first()) {
                MealReminderScheduling.schedule(appContext, slot, minutesOfDay)
            }
            refreshReminders()
        }
    }

    private suspend fun refreshRecurring() {
        val rules = workoutRepository.recurringRules()
        _uiState.update { it.copy(recurring = rules) }
    }

    fun setShowRecurringDialog(show: Boolean) =
        _uiState.update { it.copy(showRecurringDialog = show) }

    /** Adds a rule; if it covers today, today's entry appears immediately. */
    fun addRecurring(type: WorkoutType, amount: Int, days: Set<DayOfWeek>) {
        _uiState.update { it.copy(showRecurringDialog = false) }
        if (amount <= 0 || days.isEmpty()) return
        viewModelScope.launch {
            workoutRepository.addRecurring(
                RecurringWorkoutEntity(
                    type = type,
                    minutes = if (type.kind == WorkoutKind.DURATION) amount else null,
                    reps = if (type.kind == WorkoutKind.REPS) amount else null,
                    // Sorted so "Mon, Tue" never renders as "Tue, Mon".
                    days = days.sorted(),
                ),
                today = LocalDate.now(),
            )
            refreshRecurring()
        }
    }

    fun deleteRecurring(rule: RecurringWorkoutEntity) {
        viewModelScope.launch {
            workoutRepository.deleteRecurring(rule.id)
            refreshRecurring()
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

    fun selectCookingSessions(sessions: Int) =
        _uiState.update { it.copy(cookingSessionsPerWeek = sessions) }

    fun toggleVegetarian(enabled: Boolean) = _uiState.update { it.copy(isVegetarian = enabled) }

    fun toggleNoPork(enabled: Boolean) = _uiState.update { it.copy(noPork = enabled) }

    fun toggleAllergen(allergen: Allergen) = _uiState.update { state ->
        val newSet =
            if (allergen in state.allergies) state.allergies - allergen
            else state.allergies + allergen
        state.copy(allergies = newSet)
    }

    /**
     * Turning manual targets ON with empty fields prefills them with what the
     * automatic math computes RIGHT NOW — the user edits numbers, not a void.
     */
    fun toggleManualTargets(enabled: Boolean) = _uiState.update { state ->
        val weightKg = state.weightKg
        val heightCm = state.heightCm
        if (enabled && state.customKcalText.isBlank() && weightKg != null && heightCm != null) {
            val age = CalorieCalculator.ageYears(state.birthDate, LocalDate.now())
            val kcal = CalorieCalculator.baseTargetKcal(
                state.sex, weightKg, heightCm.toDouble(), age,
                state.activityLevel, state.goal, state.targetKgPerWeek,
            )
            val targets = CalorieCalculator.macroTargets(kcal, weightKg)
            // coerceIn: for extreme profiles (e.g. 300 kg) the automatic math
            // can exceed the form's ranges — the prefill must never produce a
            // value the form itself immediately rejects (review-caught).
            state.copy(
                manualTargets = true,
                customKcalText = targets.kcal.coerceIn(
                    CalorieCalculator.safetyFloorKcal(state.sex).toInt(), 6000
                ).toString(),
                customProteinText = targets.proteinG.coerceIn(10, 400).toString(),
                customFatText = targets.fatG.coerceIn(10, 300).toString(),
                customCarbsText = targets.carbsG.coerceIn(0, 800).toString(),
            )
        } else {
            state.copy(manualTargets = enabled)
        }
    }

    fun setCustomKcalText(text: String) = _uiState.update { it.copy(customKcalText = text) }

    fun setCustomProteinText(text: String) = _uiState.update { it.copy(customProteinText = text) }

    fun setCustomFatText(text: String) = _uiState.update { it.copy(customFatText = text) }

    fun setCustomCarbsText(text: String) = _uiState.update { it.copy(customCarbsText = text) }

    fun setShowResetDialog(show: Boolean) = _uiState.update { it.copy(showResetDialog = show) }

    /** Saves the profile and today's weight; the screen navigates back on `saved`. */
    fun setAiKeyText(value: String) = _uiState.update { it.copy(aiKeyText = value) }

    /**
     * Writes immediately, like the reminder times and unlike the profile form:
     * save() is gated on canSave, and refusing to store a key because the
     * weight field happens to be empty would make no sense.
     */
    fun saveAiKey() {
        val key = _uiState.value.aiKeyText.trim()
        if (!_uiState.value.aiKeyValid) return
        viewModelScope.launch {
            secrets.setAiApiKey(key)
            _uiState.update { it.copy(aiKeyText = key, aiKeyStored = true) }
        }
    }

    fun deleteAiKey() {
        viewModelScope.launch {
            secrets.clearAiApiKey()
            _uiState.update { it.copy(aiKeyText = "", aiKeyStored = false) }
        }
    }

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
                    cookingSessionsPerWeek = state.cookingSessionsPerWeek,
                    isVegetarian = state.isVegetarian,
                    noPork = state.noPork,
                    allergies = state.allergies.toList(),
                    createdAtEpochMillis = state.createdAtEpochMillis,
                    // All four together or none — canSave guarantees validity.
                    customKcalTarget = if (state.manualTargets) state.customKcal else null,
                    customProteinG = if (state.manualTargets) state.customProtein else null,
                    customFatG = if (state.manualTargets) state.customFat else null,
                    customCarbsG = if (state.manualTargets) state.customCarbs else null,
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
