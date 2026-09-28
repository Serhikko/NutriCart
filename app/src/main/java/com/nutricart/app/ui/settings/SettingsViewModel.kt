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
import com.nutricart.app.data.remote.TelegramApi
import com.nutricart.app.cloud.CloudAccount
import com.nutricart.app.cloud.CloudPartner
import com.nutricart.app.cloud.CloudRepository
import com.nutricart.app.cloud.CloudResult
import com.nutricart.app.partner.PartnerRepository
import com.nutricart.app.partner.PartnerResult
import com.nutricart.app.partner.PartnerScheduling
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
    /** Shown right after a successful save; cleared as soon as the field changes. */
    val aiKeySavedNotice: Boolean = false,
    // --- Partner sharing (Telegram) ---
    /** The bot token as typed (masked on screen). */
    val botTokenText: String = "",
    val botTokenStored: Boolean = false,
    /** The bot's @username once Telegram confirmed the token. */
    val botUsername: String? = null,
    /** The linked partner's name; null = not connected. */
    val partnerName: String? = null,
    val partnerShareMeals: Boolean = true,
    val partnerNotifyMissed: Boolean = true,
    val partnerInboxEnabled: Boolean = true,
    /** True while a Telegram call is in flight — the buttons wait. */
    val partnerBusy: Boolean = false,
    /** One-shot outcome of the last partner action, shown under the section. */
    val partnerNotice: PartnerNotice? = null,
    // --- Cloud sync (Supabase) ---
    /** False in a build without keys: the section shows one line and nothing else. */
    val cloudConfigured: Boolean = false,
    val cloudEnabled: Boolean = false,
    /** The name as typed; saved on Save, prefilled from the store. */
    val cloudNameText: String = "",
    val cloudNameStored: String? = null,
    /** The live pairing code and when it expires (epoch millis); null = none. */
    val cloudPairingCode: Pair<String, Long>? = null,
    val cloudPartners: List<CloudPartner> = emptyList(),
    val cloudLastSyncEpochMillis: Long? = null,
    /** Short error code of the last failed sync, from CloudSyncWorker; null = fine. */
    val cloudLastError: String? = null,
    val cloudPendingCount: Int = 0,
    val cloudBusy: Boolean = false,
    val cloudNotice: CloudNotice? = null,
    /** The email field, and what the server says about the account's email (null = not asked yet). */
    val cloudEmailText: String = "",
    val cloudAccount: CloudAccount? = null,
    /** The switch was turned on but no name is stored yet: ask for one first. */
    val showCloudNameDialog: Boolean = false,
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

    val botTokenValid: Boolean
        get() = TelegramApi.TOKEN_PATTERN.matches(botTokenText.trim())

    val cloudNameValid: Boolean
        get() = cloudNameText.trim().length in 1..40

    val cloudEmailValid: Boolean
        get() = cloudEmailText.trim().let { it.contains('@') && it.substringAfter('@').contains('.') }
}

/** What the cloud section reports after an action. */
enum class CloudNotice { ENABLED, NAME_SAVED, CODE_READY, UNLINKED, EMAIL_SENT, NOT_CONFIGURED, OFFLINE, AUTH, FAILED }

/** What the partner section reports after an action; each maps to one string. */
enum class PartnerNotice {
    BOT_SAVED, LINKED, TEST_SENT, NO_MESSAGE_YET, BAD_TOKEN, OFFLINE, BUSY, BLOCKED, FAILED,
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
    private val partnerRepository: PartnerRepository,
    private val partnerScheduling: PartnerScheduling,
    private val cloudRepository: CloudRepository,
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
        // Same rule for the bot token field…
        viewModelScope.launch {
            val stored = secrets.telegramBotToken.first()
            _uiState.update {
                it.copy(botTokenText = stored.orEmpty(), botTokenStored = !stored.isNullOrBlank())
            }
        }
        // …while the link, the bot name and the toggles are not typed into,
        // so they can follow the store live.
        viewModelScope.launch {
            partnerRepository.link.collect { link ->
                _uiState.update { it.copy(partnerName = link?.name) }
            }
        }
        viewModelScope.launch {
            partnerRepository.botUsername.collect { username ->
                _uiState.update { it.copy(botUsername = username) }
            }
        }
        viewModelScope.launch {
            settings.partnerShareMeals.collect { on -> _uiState.update { it.copy(partnerShareMeals = on) } }
        }
        viewModelScope.launch {
            settings.partnerNotifyMissed.collect { on -> _uiState.update { it.copy(partnerNotifyMissed = on) } }
        }
        viewModelScope.launch {
            settings.partnerInboxEnabled.collect { on -> _uiState.update { it.copy(partnerInboxEnabled = on) } }
        }

        // Cloud sync: the flags follow the store live; the name field is read
        // once (it is typed into), then kept in step by saveCloudName.
        _uiState.update { it.copy(cloudConfigured = cloudRepository.isConfigured) }
        viewModelScope.launch {
            val stored = cloudRepository.displayName.first()
            _uiState.update { it.copy(cloudNameText = stored.orEmpty(), cloudNameStored = stored) }
        }
        viewModelScope.launch {
            cloudRepository.isEnabled.collect { on ->
                _uiState.update { it.copy(cloudEnabled = on) }
                if (on) {
                    refreshCloudPartners()
                    refreshCloudAccount()
                }
            }
        }
        viewModelScope.launch {
            cloudRepository.pairingCode.collect { code -> _uiState.update { it.copy(cloudPairingCode = code) } }
        }
        viewModelScope.launch {
            cloudRepository.lastSyncEpochMillis.collect { t -> _uiState.update { it.copy(cloudLastSyncEpochMillis = t) } }
        }
        viewModelScope.launch {
            cloudRepository.lastError.collect { e -> _uiState.update { it.copy(cloudLastError = e) } }
        }
        viewModelScope.launch {
            cloudRepository.pendingCount.collect { n -> _uiState.update { it.copy(cloudPendingCount = n) } }
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
    fun setAiKeyText(value: String) =
        _uiState.update { it.copy(aiKeyText = value, aiKeySavedNotice = false) }

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
            _uiState.update {
                it.copy(aiKeyText = key, aiKeyStored = true, aiKeySavedNotice = true)
            }
        }
    }

    fun deleteAiKey() {
        viewModelScope.launch {
            secrets.clearAiApiKey()
            _uiState.update { it.copy(aiKeyText = "", aiKeyStored = false) }
        }
    }

    // --- Partner sharing ---

    fun setBotTokenText(value: String) =
        _uiState.update { it.copy(botTokenText = value, partnerNotice = null) }

    fun clearPartnerNotice() = _uiState.update { it.copy(partnerNotice = null) }

    /** Runs one partner action with the busy flag and turns its result into a notice. */
    private fun partnerAction(onOk: PartnerNotice, block: suspend () -> PartnerResult) {
        if (_uiState.value.partnerBusy) return // one call at a time
        _uiState.update { it.copy(partnerBusy = true, partnerNotice = null) }
        viewModelScope.launch {
            val result = try {
                block()
            } finally {
                _uiState.update { it.copy(partnerBusy = false) }
            }
            _uiState.update { it.copy(partnerNotice = noticeFor(result, onOk)) }
        }
    }

    private fun noticeFor(result: PartnerResult, onOk: PartnerNotice): PartnerNotice = when (result) {
        PartnerResult.Ok -> onOk
        PartnerResult.NoMessageYet -> PartnerNotice.NO_MESSAGE_YET
        PartnerResult.BadToken, PartnerResult.NoToken -> PartnerNotice.BAD_TOKEN
        PartnerResult.Offline -> PartnerNotice.OFFLINE
        PartnerResult.Busy -> PartnerNotice.BUSY
        PartnerResult.Blocked -> PartnerNotice.BLOCKED
        PartnerResult.NotLinked, PartnerResult.Failed -> PartnerNotice.FAILED
    }

    /** Verifies the token with Telegram and stores it only if accepted. */
    fun saveBotToken() {
        val token = _uiState.value.botTokenText.trim()
        if (!_uiState.value.botTokenValid) return
        partnerAction(PartnerNotice.BOT_SAVED) {
            partnerRepository.saveToken(token).also { result ->
                if (result == PartnerResult.Ok) {
                    _uiState.update { it.copy(botTokenText = token, botTokenStored = true) }
                    partnerScheduling.reanchor()
                }
            }
        }
    }

    fun deleteBotToken() {
        viewModelScope.launch {
            partnerRepository.deleteToken()
            partnerScheduling.cancelAll()
            _uiState.update { it.copy(botTokenText = "", botTokenStored = false, partnerNotice = null) }
        }
    }

    /** Links whoever last wrote to the bot, then greets them (string from the UI language). */
    fun connectPartner(greeting: String) {
        partnerAction(PartnerNotice.LINKED) {
            partnerRepository.connectPartner(greeting).also { result ->
                if (result == PartnerResult.Ok) partnerScheduling.reanchor()
            }
        }
    }

    fun unlinkPartner() {
        viewModelScope.launch {
            partnerRepository.unlinkPartner()
            partnerScheduling.cancelAll()
            _uiState.update { it.copy(partnerNotice = null) }
        }
    }

    fun sendPartnerTest(text: String) {
        partnerAction(PartnerNotice.TEST_SENT) { partnerRepository.sendText(text) }
    }

    fun setPartnerShareMeals(enabled: Boolean) {
        viewModelScope.launch { settings.setPartnerShareMeals(enabled) }
    }

    fun setPartnerNotifyMissed(enabled: Boolean) {
        viewModelScope.launch { settings.setPartnerNotifyMissed(enabled) }
    }

    /** The inbox toggle also starts or stops the polling cycle. */
    fun setPartnerInboxEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setPartnerInboxEnabled(enabled)
            partnerScheduling.reanchor()
        }
    }

    // --- Cloud sync ---

    fun setCloudNameText(value: String) = _uiState.update { it.copy(cloudNameText = value, cloudNotice = null) }

    fun clearCloudNotice() = _uiState.update { it.copy(cloudNotice = null) }

    fun dismissCloudNameDialog() = _uiState.update { it.copy(showCloudNameDialog = false) }

    /**
     * The switch. Turning it on needs a display name first (the website says
     * "<name>'s day"), so without one the dialog opens and the real enable
     * happens from there. Turning it off is immediate and keeps the account.
     */
    fun toggleCloudSync(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                cloudRepository.disable()
                partnerScheduling.reanchor() // stop the nudge polling if Telegram is not linked
                _uiState.update { it.copy(cloudNotice = null, cloudPartners = emptyList()) }
            }
            return
        }
        val name = _uiState.value.cloudNameText.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(showCloudNameDialog = true) }
        } else {
            enableCloudSync(name)
        }
    }

    fun enableCloudSync(displayName: String) {
        _uiState.update { it.copy(showCloudNameDialog = false, cloudNameText = displayName.trim()) }
        cloudAction(CloudNotice.ENABLED) {
            cloudRepository.enable(displayName).also { result ->
                if (result == CloudResult.Ok) {
                    _uiState.update { it.copy(cloudNameStored = displayName.trim()) }
                    partnerScheduling.reanchor() // nudges from the website ride the inbox cycle
                    refreshCloudPartners()
                }
            }
        }
    }

    fun saveCloudName() {
        val name = _uiState.value.cloudNameText.trim()
        if (!_uiState.value.cloudNameValid) return
        cloudAction(CloudNotice.NAME_SAVED) {
            cloudRepository.setDisplayName(name).also { result ->
                if (result == CloudResult.Ok) _uiState.update { it.copy(cloudNameStored = name) }
            }
        }
    }

    fun newPairingCode() {
        cloudAction(CloudNotice.CODE_READY) { cloudRepository.newPairingCode() }
    }

    fun refreshCloudPartners() {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudPartners = cloudRepository.partners()) }
        }
    }

    // --- Account email (milestone 3) ---

    fun setCloudEmailText(value: String) = _uiState.update { it.copy(cloudEmailText = value, cloudNotice = null) }

    /** Asks the server whether the account has an email yet; the field defaults to the one typed last. */
    fun refreshCloudAccount() {
        viewModelScope.launch {
            val stored = cloudRepository.linkedEmail.first()
            val account = cloudRepository.account()
            _uiState.update {
                it.copy(
                    cloudAccount = account,
                    cloudEmailText = it.cloudEmailText.ifEmpty { stored ?: account?.pendingEmail.orEmpty() },
                )
            }
        }
    }

    fun linkCloudEmail() {
        val email = _uiState.value.cloudEmailText.trim()
        if (!_uiState.value.cloudEmailValid) return
        cloudAction(CloudNotice.EMAIL_SENT) {
            cloudRepository.linkEmail(email).also { result ->
                if (result == CloudResult.Ok) refreshCloudAccount()
            }
        }
    }

    fun unlinkCloudPartner(linkId: String) {
        cloudAction(CloudNotice.UNLINKED) {
            cloudRepository.unlink(linkId).also { refreshCloudPartners() }
        }
    }

    private fun cloudAction(onOk: CloudNotice, block: suspend () -> CloudResult) {
        if (_uiState.value.cloudBusy) return
        _uiState.update { it.copy(cloudBusy = true, cloudNotice = null) }
        viewModelScope.launch {
            val result = try {
                block()
            } finally {
                _uiState.update { it.copy(cloudBusy = false) }
            }
            val notice = when (result) {
                CloudResult.Ok -> onOk
                CloudResult.NotConfigured -> CloudNotice.NOT_CONFIGURED
                CloudResult.Offline -> CloudNotice.OFFLINE
                CloudResult.Auth -> CloudNotice.AUTH
                CloudResult.Failed -> CloudNotice.FAILED
            }
            _uiState.update { it.copy(cloudNotice = notice) }
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
