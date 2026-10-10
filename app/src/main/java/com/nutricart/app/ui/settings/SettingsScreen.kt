package com.nutricart.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.workoutTypeLabel
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.BackLink
import com.nutricart.app.ui.ember.BottomClearance
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.DateField
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberConfirmSheet
import com.nutricart.app.ui.ember.EmberDurations
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.EmberSpace
import com.nutricart.app.ui.ember.EmberSpinner
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmberSwitch
import com.nutricart.app.ui.ember.EmberTextField
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.FooterTone
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.MealTiles
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.SectionFooter
import com.nutricart.app.ui.ember.SectionHeader
import com.nutricart.app.ui.ember.SheetButtonPair
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SwitchRow
import com.nutricart.app.ui.ember.TimePill
import com.nutricart.app.ui.ember.WholeWordsText
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberSheetCloser
import com.nutricart.app.ui.navigation.LocalBackLabel
import com.nutricart.app.ui.onboarding.ActivityRows
import com.nutricart.app.ui.onboarding.AllergyChips
import com.nutricart.app.ui.onboarding.BlockLabel
import com.nutricart.app.ui.onboarding.CookingControl
import com.nutricart.app.ui.onboarding.GoalRows
import com.nutricart.app.ui.onboarding.MeasurementFields
import com.nutricart.app.ui.onboarding.RateControl
import com.nutricart.app.ui.onboarding.SexControl
import com.nutricart.app.ui.onboarding.SnacksControl
import com.nutricart.app.ui.onboarding.StackFontScale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/**
 * One scrollable form with the same fields as onboarding, pre-filled from the
 * saved profile. Saving updates the profile and logs today's weight; the
 * dashboard recomputes automatically because it observes the database.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Leave the screen once saving is done.
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    SettingsContent(state = state, onBack = onBack, actions = viewModel)
}

/**
 * Everything the settings screen can ask for. [SettingsViewModel] implements
 * it; screenshot tests pass a no-op implementation.
 */
interface SettingsActions {
    fun toggleReminder(slot: MealSlot, enabled: Boolean)
    fun startEditingReminder(slot: MealSlot)
    fun cancelEditingReminder()
    fun setReminderTime(minutesOfDay: Int)
    fun setShowRecurringDialog(show: Boolean)
    fun addRecurring(type: WorkoutType, amount: Int, days: Set<DayOfWeek>)
    fun deleteRecurring(rule: RecurringWorkoutEntity)
    fun selectSex(sex: Sex)
    fun selectBirthDate(date: LocalDate)
    fun setHeightText(text: String)
    fun setWeightText(text: String)
    fun selectActivityLevel(level: ActivityLevel)
    fun selectGoal(goal: Goal)
    fun selectRate(rate: Double)
    fun selectSnacksPerDay(count: Int)
    fun selectCookingSessions(sessions: Int)
    fun toggleVegetarian(enabled: Boolean)
    fun toggleNoPork(enabled: Boolean)
    fun toggleAllergen(allergen: Allergen)
    fun toggleManualTargets(enabled: Boolean)
    fun setCustomKcalText(text: String)
    fun setCustomProteinText(text: String)
    fun setCustomFatText(text: String)
    fun setCustomCarbsText(text: String)
    fun setShowResetDialog(show: Boolean)
    fun setAiKeyText(value: String)
    fun saveAiKey()
    fun deleteAiKey()
    fun setBotTokenText(value: String)
    fun saveBotToken()
    fun deleteBotToken()
    fun connectPartner(greeting: String)
    fun unlinkPartner()
    fun sendPartnerTest(text: String)
    fun setPartnerShareMeals(enabled: Boolean)
    fun setPartnerNotifyMissed(enabled: Boolean)
    fun setPartnerInboxEnabled(enabled: Boolean)
    fun setCloudNameText(value: String)
    fun dismissCloudNameDialog()
    fun toggleCloudSync(enabled: Boolean)
    fun enableCloudSync(displayName: String)
    fun saveCloudName()
    fun newPairingCode()
    fun setCloudEmailText(value: String)
    fun setCloudPasswordText(value: String)
    fun generateCloudPassword()
    fun linkCloudEmail()
    fun unlinkCloudPartner(linkId: String)
    fun save()
    fun confirmReset()
}

/** The list key of the Save button, so the floating "Save changes" knows when it is on screen. */
private const val SaveKey = "save"

/**
 * The stateless half of [SettingsScreen]: the form and the sheets [state] asks for.
 *
 * Two save models live on this screen, and the order shows them (INV §13.11): first everything the
 * Save button saves (the profile, targets, food preferences), then Save itself with a note, then
 * everything that saves the moment it changes (regular activities, reminders, the AI key, the
 * partner, the cloud). While the form has unsaved edits and Save is off screen, a floating "Save
 * changes" capsule does the same save. [backLabel] names the screen below ("‹ Today").
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    actions: SettingsActions,
    backLabel: String = LocalBackLabel.current ?: stringResource(R.string.back),
) {
    val listState = rememberLazyListState()
    val first = rememberFirstOpen("settings")
    val entrances = rememberEntranceState()

    // Unsaved edits: the Save-gated part of the form against what it held when it loaded. The
    // ViewModel keeps no such flag, so the screen remembers the loaded form itself (UI state only).
    val form = profileForm(state)
    var loadedForm by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loading) {
        if (!state.loading && loadedForm == null) loadedForm = form
    }
    val dirty = !state.loading && !state.saved && loadedForm != null && loadedForm != form
    val saveInView by remember(listState) { derivedStateOf { listState.isItemInView(SaveKey) } }

    // Android 13+ needs the runtime permission before a reminder or a partner message can show.
    // Denied = they stay scheduled (or fetched) but silent; nothing else to do.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = stringResource(R.string.settings_title),
            back = BackLink(backLabel, onBack),
            // With the capsule up, the end of the list keeps clear of it.
            bottom = if (dirty) BottomClearance.DockedCta else BottomClearance.Stacked,
            listState = listState,
        ) {
            if (state.loading) {
                loadingItems()
            } else {
                formItems(state, actions, askForNotifications, first, entrances)
            }
        }
        SaveCapsule(
            visible = dirty && !saveInView,
            enabled = state.canSave && !state.saved,
            onClick = actions::save,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }

    if (state.showResetDialog) {
        EmberConfirmSheet(
            title = stringResource(R.string.reset_confirm_title),
            text = stringResource(R.string.reset_confirm_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = actions::confirmReset,
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { actions.setShowResetDialog(false) },
            icon = EmberIcons.Trash,
            destructive = true,
        )
    }

    if (state.showCloudNameDialog) {
        CloudNameSheet(
            initialName = state.cloudNameText,
            onConfirm = actions::enableCloudSync,
            onDismiss = actions::dismissCloudNameDialog,
        )
    }

    if (state.showRecurringDialog) {
        RecurringSheet(
            onConfirm = actions::addRecurring,
            onDismiss = { actions.setShowRecurringDialog(false) },
        )
    }

    state.editingReminderSlot?.let { slot ->
        val minutes = state.reminders.firstOrNull { it.slot == slot }?.minutesOfDay ?: 0
        ReminderTimeSheet(
            slot = slot,
            initialMinutes = minutes,
            onConfirm = actions::setReminderTime,
            onDismiss = actions::cancelEditingReminder,
        )
    }
}

/** Everything the Save button saves, as one comparable value (it survives rotation as a string). */
private fun profileForm(s: SettingsUiState): String = listOf(
    s.sex, s.birthDate, s.heightCmText, s.weightKgText, s.activityLevel, s.goal, s.targetKgPerWeek,
    s.snacksPerDay, s.cookingSessionsPerWeek, s.isVegetarian, s.noPork, s.allergies.sorted(),
    s.manualTargets, s.customKcalText, s.customProteinText, s.customFatText, s.customCarbsText,
).joinToString("|")

/** True when the middle of the item [key] is inside the list's visible window. */
private fun LazyListState.isItemInView(key: Any): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    val middle = item.offset + item.size / 2
    return middle in (info.viewportStartOffset + info.beforeContentPadding)..(info.viewportEndOffset - info.afterContentPadding)
}

// ---------- The list ----------

/** While the profile loads: grouped placeholders where the first sections will be. */
private fun LazyListScope.loadingItems() {
    val heights = listOf(120, 104, 280)
    heights.forEachIndexed { i, h ->
        item(key = "loading-$i") {
            val loading = stringResource(R.string.loading)
            Column(
                Modifier
                    .closeListGap()
                    .then(if (i == 0) Modifier.semantics(mergeDescendants = true) { contentDescription = loading } else Modifier),
            ) {
                SkeletonLine(
                    width = if (i == 0) 88.dp else 136.dp,
                    modifier = Modifier.padding(start = 16.dp, top = EmberSpace.GroupHeadTop, bottom = 8.dp),
                )
                Skeleton(Modifier.fillMaxWidth().height(h.dp))
            }
        }
    }
}

private fun LazyListScope.formItems(
    state: SettingsUiState,
    actions: SettingsActions,
    askForNotifications: () -> Unit,
    first: Boolean,
    entrances: EntranceState,
) {
    // The sections rise in a short cascade on the first open of the day; later ones (scrolled to)
    // simply appear.
    fun Modifier.enter(index: Int, key: String) = emberEntrance(index, EntranceKind.Rise, first, entrances, key)

    // Saved with the Save button.
    item(key = "about") { AboutSection(state, actions, Modifier.enter(0, "about")) }
    item(key = "body") { BodySection(state, actions, Modifier.enter(1, "body")) }
    item(key = "activity") { ActivitySection(state, actions, Modifier.enter(2, "activity")) }
    item(key = "goal") { GoalSection(state, actions, Modifier.enter(3, "goal")) }
    item(key = "targets") { TargetsSection(state, actions, Modifier.enter(4, "targets")) }
    item(key = "diet") { DietSection(state, actions, Modifier.enter(5, "diet")) }
    item(key = SaveKey) { SaveBlock(state, actions, Modifier.enter(6, SaveKey)) }

    // Saved as soon as they change.
    item(key = "recurring") { RecurringSection(state, actions, Modifier.enter(7, "recurring")) }
    item(key = "reminders") { RemindersSection(state, actions, askForNotifications, Modifier.enter(8, "reminders")) }
    item(key = "ai") { AiSection(state, actions, Modifier.enter(8, "ai")) }
    item(key = "partner") { PartnerSection(state, actions, askForNotifications, Modifier.enter(8, "partner")) }
    item(key = "cloud") { CloudSection(state, actions, Modifier.enter(8, "cloud")) }
    if (state.hcAvailable) {
        item(key = "hc") { HealthConnectSection(state, Modifier.enter(8, "hc")) }
    }
    item(key = "reset") { ResetSection(actions, Modifier.enter(8, "reset")) }
}

/**
 * Cancels the list's 12 dp item gap above a section, so its head sits 22 dp under the group or note
 * before it: the inset-list rhythm, with the gap the scaffold keeps for card stacks.
 */
private fun Modifier.closeListGap(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val gap = EmberSpace.StackGap.roundToPx()
    layout(placeable.width, (placeable.height - gap).coerceAtLeast(0)) { placeable.place(0, -gap) }
}

/** One section of the form: its head (a heading), then the group and notes [content] draws. */
@Composable
private fun Section(header: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.closeListGap()) {
        if (header != null) SectionHeader(header) else Spacer(Modifier.height(EmberSpace.s6))
        content()
    }
}

// ---------- Saved with the Save button ----------

@Composable
private fun AboutSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    val title = stringResource(R.string.onboarding_title_sex)
    Section(title, modifier) {
        InsetGroup {
            row { SexControl(state.sex, actions::selectSex, title, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            row {
                DateField(
                    label = stringResource(R.string.onboarding_title_birth),
                    date = state.birthDate,
                    onPick = actions::selectBirthDate,
                    placeholder = stringResource(R.string.choose_date),
                )
            }
        }
        if (state.underageBlocked) {
            Notice(NoticeTone.Warning, stringResource(R.string.underage_error), Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun BodySection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.onboarding_title_body), modifier) {
        InsetGroup {
            row {
                MeasurementFields(
                    heightText = state.heightCmText,
                    weightText = state.weightKgText,
                    heightInvalid = state.heightCmText.isNotEmpty() && state.heightCm == null,
                    weightInvalid = state.weightKgText.isNotEmpty() && state.weightKg == null,
                    onHeightChange = actions::setHeightText,
                    onWeightChange = actions::setWeightText,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        SectionFooter(stringResource(R.string.weight_edit_hint))
    }
}

@Composable
private fun ActivitySection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.onboarding_title_activity), modifier) {
        InsetGroup { ActivityRows(state.activityLevel, actions::selectActivityLevel) }
    }
}

@Composable
private fun GoalSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.onboarding_title_goal), modifier) {
        InsetGroup {
            GoalRows(state.goal, actions::selectGoal)
            if (state.goal != Goal.MAINTAIN) {
                row {
                    RateControl(
                        state.targetKgPerWeek,
                        actions::selectRate,
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The manual override: a switch, and when it is on the four numbers in a 2 × 2 grid (one column at
 * large text sizes), each with the shared "out of range" error.
 */
@Composable
private fun TargetsSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.targets_section), modifier) {
        InsetGroup {
            row {
                SwitchRow(stringResource(R.string.targets_manual_switch), state.manualTargets, actions::toggleManualTargets)
            }
            if (state.manualTargets) {
                row { TargetFields(state, actions, Modifier.padding(16.dp)) }
            }
        }
        if (state.manualTargets) SectionFooter(stringResource(R.string.targets_manual_note))
    }
}

@Composable
private fun TargetFields(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    val error = stringResource(R.string.target_invalid)
    val field: @Composable (String, (String) -> Unit, Int, Boolean, Modifier) -> Unit = { value, onChange, labelRes, invalid, m ->
        EmberTextField(
            value = value,
            onValueChange = onChange,
            label = stringResource(labelRes),
            modifier = m,
            isError = invalid,
            errorText = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
    }
    val kcal: @Composable (Modifier) -> Unit = {
        field(state.customKcalText, actions::setCustomKcalText, R.string.target_kcal_label,
            state.customKcalText.isNotEmpty() && state.customKcal == null, it)
    }
    val protein: @Composable (Modifier) -> Unit = {
        field(state.customProteinText, actions::setCustomProteinText, R.string.target_protein_label,
            state.customProteinText.isNotEmpty() && state.customProtein == null, it)
    }
    val fat: @Composable (Modifier) -> Unit = {
        field(state.customFatText, actions::setCustomFatText, R.string.target_fat_label,
            state.customFatText.isNotEmpty() && state.customFat == null, it)
    }
    val carbs: @Composable (Modifier) -> Unit = {
        field(state.customCarbsText, actions::setCustomCarbsText, R.string.target_carbs_label,
            state.customCarbsText.isNotEmpty() && state.customCarbs == null, it)
    }
    if (LocalDensity.current.fontScale >= StackFontScale) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(kcal, protein, fat, carbs).forEach { it(Modifier.fillMaxWidth()) }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                kcal(Modifier.weight(1f))
                protein(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fat(Modifier.weight(1f))
                carbs(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DietSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.onboarding_title_diet), modifier) {
        InsetGroup {
            row { SwitchRow(stringResource(R.string.vegetarian_label), state.isVegetarian, actions::toggleVegetarian) }
            row { SwitchRow(stringResource(R.string.no_pork_label), state.noPork, actions::toggleNoPork) }
            row {
                LabelledBlock(stringResource(R.string.allergies_label)) {
                    AllergyChips(state.allergies, actions::toggleAllergen)
                }
            }
            row {
                LabelledBlock(stringResource(R.string.snacks_label)) {
                    SnacksControl(state.snacksPerDay, actions::selectSnacksPerDay)
                }
            }
            row {
                LabelledBlock(stringResource(R.string.cooking_label)) {
                    CookingControl(state.cookingSessionsPerWeek, actions::selectCookingSessions)
                }
            }
        }
    }
}

/** A control under its small label, inside a group row. */
@Composable
private fun LabelledBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        BlockLabel(label)
        content()
    }
}

/** Save (it leaves the screen when done) and the note that everything below saves by itself. */
@Composable
private fun SaveBlock(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    val c = Ember.colors
    Column(modifier.padding(top = 12.dp)) {
        EmberButton(
            stringResource(R.string.save),
            onClick = actions::save,
            size = ButtonSize.Lg,
            enabled = state.canSave && !state.saved,
        )
        BasicText(
            stringResource(R.string.settings_saves_now),
            style = Ember.type.footnote.copy(textAlign = TextAlign.Center),
            color = { c.label2 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

/**
 * "Save changes": an ink capsule floating bottom right while the form has unsaved edits and the real
 * Save button is scrolled away. It rises in on the sheet spring and drops away when the form is
 * clean again (or Save comes into view); it saves exactly as Save does.
 */
@Composable
private fun SaveCapsule(visible: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(end = EmberSpace.TabBarSide, bottom = EmberSpace.DockedCtaBottomStacked),
        enter = if (reduced) {
            EnterTransition.None
        } else {
            slideInVertically(EmberSprings.sheet()) { it * 2 } + fadeIn(tween(EmberDurations.State, easing = EmberEasing.Out))
        },
        exit = if (reduced) {
            ExitTransition.None
        } else {
            slideOutVertically(tween(240, easing = EmberEasing.In)) { it * 2 } + fadeOut(tween(200, easing = EmberEasing.In))
        },
        label = "saveCapsule",
    ) {
        EmberButton(
            stringResource(R.string.settings_save_changes),
            onClick = onClick,
            modifier = Modifier
                .emberShadow(Elevation.Float, EmberShapes.capsule, c.isDark)
                .heightIn(min = EmberSpace.TouchTarget),
            icon = EmberIcons.Check,
            enabled = enabled,
        )
    }
}

// ---------- Saved as soon as they change ----------

/** Standing workouts: "Treadmill walk · 30 min · Mon Tue" with a delete button, and the add row. */
@Composable
private fun RecurringSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.recurring_section), modifier) {
        InsetGroup {
            if (state.recurring.isEmpty()) {
                row { GroupNote(stringResource(R.string.recurring_empty)) }
            }
            state.recurring.forEach { rule ->
                row(dividerStart = IconRowDivider) {
                    RecurringRow(rule = rule, onDelete = { actions.deleteRecurring(rule) })
                }
            }
            row(dividerStart = if (state.recurring.isEmpty()) 16.dp else IconRowDivider) {
                AddRow(stringResource(R.string.recurring_add)) { actions.setShowRecurringDialog(true) }
            }
        }
    }
}

/** Rows that start with a 30 dp tile: their hairlines start after it. */
private val IconRowDivider = 58.dp

@Composable
private fun RecurringRow(rule: RecurringWorkoutEntity, onDelete: () -> Unit) {
    val amount = rule.minutes?.let { stringResource(R.string.minutes_value, it) }
        ?: rule.reps?.let { stringResource(R.string.workout_reps_value, it) }
    val locale = LocalConfiguration.current.locales[0]
    val days = rule.days.joinToString(" ") {
        it.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    }
    ListRow(
        title = stringResource(workoutTypeLabel(rule.type)),
        subtitle = listOfNotNull(amount, days).joinToString(" · "),
        leading = { IconTile(workoutIcon(rule.type)) },
        trailing = {
            EmberIconButton(
                EmberIcons.Trash,
                stringResource(R.string.recurring_delete),
                onClick = onDelete,
                style = IconButtonStyle.Plain,
                size = 40.dp,
            )
        },
    )
}

/** A glyph for a workout type, for the row's tile (the label names it; the glyph is decoration). */
private fun workoutIcon(type: WorkoutType): EmberIcons = when (type) {
    WorkoutType.RUNNING, WorkoutType.TREADMILL_WALK, WorkoutType.FOOTBALL -> EmberIcons.Steps
    WorkoutType.SWIMMING -> EmberIcons.Drop
    WorkoutType.YOGA -> EmberIcons.Heart
    WorkoutType.JUMP_ROPE -> EmberIcons.Bolt
    else -> EmberIcons.Dumbbell
}

/** Per meal: its tile and name, the time (its own button) and the switch; the row is the toggle. */
@Composable
private fun RemindersSection(
    state: SettingsUiState,
    actions: SettingsActions,
    askForNotifications: () -> Unit,
    modifier: Modifier,
) {
    Section(stringResource(R.string.reminders_section), modifier) {
        InsetGroup {
            state.reminders.forEach { reminder ->
                row(dividerStart = IconRowDivider) {
                    ReminderRow(
                        reminder = reminder,
                        onToggle = { enabled ->
                            if (enabled) askForNotifications()
                            actions.toggleReminder(reminder.slot, enabled)
                        },
                        onEditTime = { actions.startEditingReminder(reminder.slot) },
                    )
                }
            }
        }
        SectionFooter(stringResource(R.string.reminders_hint))
    }
}

/**
 * One reminder: the meal tile and name, the time pill ("08:30", a button of its own named
 * "Breakfast reminder time, 08:30") and the switch. The whole row toggles, named by the meal. At
 * large text sizes the pill moves under the name, so the name keeps its width.
 */
@Composable
private fun ReminderRow(reminder: ReminderUi, onToggle: (Boolean) -> Unit, onEditTime: () -> Unit) {
    val c = Ember.colors
    val meal = mealSlotLabel(reminder.slot)
    val time = "%02d:%02d".format(reminder.minutesOfDay / 60, reminder.minutesOfDay % 60)
    val source = remember { MutableInteractionSource() }
    val stacked = LocalDensity.current.fontScale >= StackFontScale
    val pill: @Composable () -> Unit = {
        TimePill(
            text = time,
            onClick = onEditTime,
            contentDescription = stringResource(R.string.reminder_time_cd, meal, time),
            on = reminder.enabled,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = EmberSpace.RowMinHeight)
            .clickableToggle(reminder.enabled, source, onToggle)
            // The pill's 48 dp touch area already gives the row its height: 3 dp above and below
            // make the 54 dp of the other rows, not 64. Stacked, the name and the pill need the air.
            .padding(horizontal = 16.dp, vertical = if (stacked) 8.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealTile(reminder.slot, size = 30.dp)
        Column(Modifier.weight(1f)) {
            WholeWordsText(meal, Ember.type.callout.copy(color = c.label))
            if (stacked) pill()
        }
        if (!stacked) pill()
        EmberSwitch(reminder.enabled)
    }
}

/** The row as a switch (Role.Switch), with the pressed fill and focus ring the other rows have. */
@Composable
private fun Modifier.clickableToggle(
    value: Boolean,
    source: MutableInteractionSource,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val c = Ember.colors
    return toggleable(
        value = value,
        interactionSource = source,
        indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
        role = Role.Switch,
        onValueChange = onValueChange,
    )
}

/**
 * The user's own API key for the fridge assistant.
 *
 * The note under the group is not decoration: the key really is stored in plain text, and that is
 * an accepted decision, so the screen says it, and there is always a way to delete it.
 */
@Composable
private fun AiSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.ai_section), modifier) {
        InsetGroup {
            row {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecretField(
                        value = state.aiKeyText,
                        onValueChange = actions::setAiKeyText,
                        label = stringResource(R.string.ai_key_label),
                        isError = state.aiKeyText.isNotBlank() && !state.aiKeyValid,
                        errorText = stringResource(R.string.ai_key_invalid),
                    )
                    ActionLine {
                        EmberButton(
                            stringResource(R.string.save),
                            onClick = actions::saveAiKey,
                            variant = ButtonVariant.Fill,
                            enabled = state.aiKeyValid,
                        )
                        if (state.aiKeyStored) DangerLink(stringResource(R.string.delete), actions::deleteAiKey)
                        if (state.aiKeySavedNotice) SavedMark(stringResource(R.string.ai_key_saved))
                    }
                }
            }
        }
        SectionFooter(stringResource(R.string.ai_key_hint))
    }
}

/**
 * "Share with a partner": a Telegram bot the user creates, a partner who writes /start to it, and
 * three switches. Set-up order on screen is the order things happen: token -> connect -> what to
 * share. Everything that calls Telegram waits while a call runs.
 *
 * The token field copies the AI key field on purpose (masked, no autocorrect, Save/Delete,
 * plain-text warning): both are the user's own secrets, and two different treatments would make one
 * of them look less serious.
 */
@Composable
private fun PartnerSection(
    state: SettingsUiState,
    actions: SettingsActions,
    askForNotifications: () -> Unit,
    modifier: Modifier,
) {
    val greeting = stringResource(R.string.partner_greeting)
    val testText = stringResource(R.string.partner_test_text)
    val busy = state.partnerBusy
    Section(stringResource(R.string.partner_section), modifier) {
        GroupIntro(stringResource(R.string.partner_intro))
        InsetGroup {
            row {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecretField(
                        value = state.botTokenText,
                        onValueChange = actions::setBotTokenText,
                        label = stringResource(R.string.partner_token_label),
                        isError = state.botTokenText.isNotBlank() && !state.botTokenValid,
                        errorText = stringResource(R.string.partner_token_invalid),
                        enabled = !busy,
                    )
                    ActionLine {
                        EmberButton(
                            stringResource(R.string.save),
                            onClick = actions::saveBotToken,
                            variant = ButtonVariant.Fill,
                            enabled = state.botTokenValid && !busy,
                        )
                        if (state.botTokenStored) {
                            DangerLink(stringResource(R.string.delete), actions::deleteBotToken, enabled = !busy)
                        }
                    }
                }
            }
            // Step 2: the partner. Only reachable once the bot is verified.
            if (state.botTokenStored) {
                val partnerName = state.partnerName
                if (partnerName == null) {
                    row {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.botUsername?.let { username ->
                                GroupText(stringResource(R.string.partner_connect_hint, username))
                            }
                            EmberButton(
                                stringResource(R.string.partner_connect_action),
                                onClick = { actions.connectPartner(greeting) },
                                variant = ButtonVariant.Fill,
                                enabled = !busy,
                            )
                        }
                    }
                } else {
                    row(dividerStart = IconRowDivider) {
                        PersonRow(
                            name = partnerName,
                            title = stringResource(R.string.partner_linked_label, partnerName),
                            subtitle = state.botUsername?.let { "@$it" },
                        ) {
                            DangerLink(stringResource(R.string.partner_unlink_action), actions::unlinkPartner, enabled = !busy)
                        }
                    }
                    // Step 3: what flows in each direction.
                    row {
                        SwitchRow(stringResource(R.string.partner_share_meals), state.partnerShareMeals, actions::setPartnerShareMeals)
                    }
                    row {
                        SwitchRow(stringResource(R.string.partner_notify_missed), state.partnerNotifyMissed, actions::setPartnerNotifyMissed)
                    }
                    row {
                        // Incoming nudges are notifications: the same permission the reminders need.
                        SwitchRow(stringResource(R.string.partner_inbox), state.partnerInboxEnabled, { enabled ->
                            if (enabled) askForNotifications()
                            actions.setPartnerInboxEnabled(enabled)
                        })
                    }
                    row(dividerStart = IconRowDivider) {
                        TintRow(
                            text = stringResource(R.string.partner_test_action),
                            icon = EmberIcons.Send,
                            onClick = { actions.sendPartnerTest(testText) },
                            busy = busy,
                        )
                    }
                }
            }
        }
        state.partnerNotice?.let { notice ->
            val good = notice == PartnerNotice.BOT_SAVED || notice == PartnerNotice.LINKED ||
                notice == PartnerNotice.TEST_SENT
            SectionFooter(
                partnerNoticeText(notice, state),
                live = true,
                tone = if (good) FooterTone.Good else FooterTone.Error,
            )
        }
        SectionFooter(stringResource(R.string.partner_privacy_hint))
    }
}

@Composable
private fun partnerNoticeText(notice: PartnerNotice, state: SettingsUiState): String = when (notice) {
    PartnerNotice.BOT_SAVED -> stringResource(R.string.partner_bot_saved, state.botUsername ?: "")
    PartnerNotice.LINKED -> stringResource(R.string.partner_notice_linked, state.partnerName ?: "")
    PartnerNotice.TEST_SENT -> stringResource(R.string.partner_notice_test_sent)
    PartnerNotice.NO_MESSAGE_YET -> stringResource(R.string.partner_notice_no_message)
    PartnerNotice.BAD_TOKEN -> stringResource(R.string.partner_notice_bad_token)
    PartnerNotice.OFFLINE -> stringResource(R.string.partner_notice_offline)
    PartnerNotice.BUSY -> stringResource(R.string.partner_notice_busy)
    PartnerNotice.BLOCKED -> stringResource(R.string.partner_notice_blocked)
    PartnerNotice.FAILED -> stringResource(R.string.partner_notice_failed)
}

/**
 * "Cloud sync & website": one switch, a name, the account email, a pairing code, the people who
 * can read the account. Everything the website needs from the phone is set up here; the schema and
 * policies are in supabase/.
 */
@Composable
private fun CloudSection(state: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    Section(stringResource(R.string.cloud_section), modifier) {
        if (!state.cloudConfigured) {
            // A build without keys (see supabase/README.md): say so, offer nothing.
            SectionFooter(stringResource(R.string.cloud_not_configured))
            return@Section
        }
        val busy = state.cloudBusy
        GroupIntro(stringResource(R.string.cloud_intro))
        InsetGroup {
            row {
                // The upload status rides under the switch while sync is on.
                val status = if (state.cloudEnabled) {
                    listOfNotNull(
                        stringResource(
                            R.string.cloud_last_sync,
                            state.cloudLastSyncEpochMillis?.let { formattedDateTime(it) }
                                ?: stringResource(R.string.no_data_dash),
                        ),
                        state.cloudPendingCount.takeIf { it > 0 }?.let { stringResource(R.string.cloud_pending, it) },
                    ).joinToString(" · ")
                } else {
                    null
                }
                SwitchRow(stringResource(R.string.cloud_switch), state.cloudEnabled, actions::toggleCloudSync, subtitle = status)
            }
            if (state.cloudEnabled) {
                row { CloudNameBlock(state, actions, busy) }
                row { CloudEmailBlock(state, actions, busy) }
                row { PairingBlock(state, actions, busy) }
                row {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                        BlockLabel(stringResource(R.string.cloud_partners_title))
                        if (state.cloudPartners.isEmpty()) {
                            GroupText(stringResource(R.string.cloud_partners_none), Modifier.padding(top = 4.dp, bottom = 8.dp))
                        }
                    }
                }
                state.cloudPartners.forEach { partner ->
                    row(dividerStart = IconRowDivider) {
                        PersonRow(name = partner.name, title = partner.name) {
                            DangerLink(
                                stringResource(R.string.cloud_unlink),
                                { actions.unlinkCloudPartner(partner.linkId) },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }
        if (state.cloudEnabled) {
            state.cloudLastError?.let { error -> SectionFooter(cloudErrorText(error), tone = FooterTone.Error) }
        }
        state.cloudNotice?.let { notice ->
            val good = notice == CloudNotice.ENABLED || notice == CloudNotice.NAME_SAVED ||
                notice == CloudNotice.CODE_READY || notice == CloudNotice.UNLINKED || notice == CloudNotice.EMAIL_SENT
            SectionFooter(
                stringResource(
                    when (notice) {
                        CloudNotice.ENABLED -> R.string.cloud_notice_enabled
                        CloudNotice.NAME_SAVED -> R.string.cloud_notice_name_saved
                        CloudNotice.CODE_READY -> R.string.cloud_notice_code_ready
                        CloudNotice.UNLINKED -> R.string.cloud_notice_unlinked
                        CloudNotice.EMAIL_SENT -> R.string.cloud_notice_email_sent
                        CloudNotice.NOT_CONFIGURED -> R.string.cloud_not_configured
                        CloudNotice.OFFLINE -> R.string.cloud_notice_offline
                        CloudNotice.AUTH -> R.string.cloud_notice_auth
                        CloudNotice.FAILED -> R.string.cloud_notice_failed
                    },
                ),
                live = true,
                tone = if (good) FooterTone.Good else FooterTone.Error,
            )
        }
        SectionFooter(stringResource(R.string.cloud_privacy_hint))
    }
}

/** The name the website shows; Save appears only when the field differs from the stored name. */
@Composable
private fun CloudNameBlock(state: SettingsUiState, actions: SettingsActions, busy: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmberTextField(
            value = state.cloudNameText,
            onValueChange = actions::setCloudNameText,
            label = stringResource(R.string.cloud_name_label),
            modifier = Modifier.weight(1f),
            isError = state.cloudNameText.isNotEmpty() && !state.cloudNameValid,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            enabled = !busy,
        )
        if (state.cloudNameText.trim() != state.cloudNameStored.orEmpty()) {
            EmberButton(
                stringResource(R.string.save),
                onClick = actions::saveCloudName,
                enabled = state.cloudNameValid && !busy,
            )
        }
    }
}

/** Account email: the way into this account from the website and from a new phone. */
@Composable
private fun CloudEmailBlock(state: SettingsUiState, actions: SettingsActions, busy: Boolean) {
    val c = Ember.colors
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BlockLabel(stringResource(R.string.cloud_email_title))
        val account = state.cloudAccount
        if (account?.email != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberIcon(EmberIcons.Check, null, size = 20.dp, tint = c.good)
                BasicText(
                    stringResource(R.string.cloud_email_linked, account.email),
                    style = Ember.type.callout,
                    color = { c.label },
                )
            }
        } else {
            GroupText(stringResource(R.string.cloud_email_hint))
            EmberTextField(
                value = state.cloudEmailText,
                onValueChange = actions::setCloudEmailText,
                label = stringResource(R.string.cloud_email_label),
                isError = state.cloudEmailText.isNotEmpty() && !state.cloudEmailValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                enabled = !busy,
            )
            // The password is shown in clear on purpose: the user reads it off this screen to type
            // it into the website, and it is never stored here.
            EmberTextField(
                value = state.cloudPasswordText,
                onValueChange = actions::setCloudPasswordText,
                label = stringResource(R.string.cloud_password_label),
                isError = state.cloudPasswordText.isNotEmpty() && !state.cloudPasswordValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
            )
            EmberButton(
                stringResource(R.string.cloud_password_generate),
                onClick = actions::generateCloudPassword,
                variant = ButtonVariant.Fill,
                icon = EmberIcons.Sparkle,
                enabled = !busy,
            )
            EmberButton(
                stringResource(R.string.cloud_email_link),
                onClick = actions::linkCloudEmail,
                icon = EmberIcons.Send,
                enabled = state.cloudEmailValid && state.cloudPasswordValid && !busy,
            )
            account?.pendingEmail?.let { pending ->
                GroupText(stringResource(R.string.cloud_email_pending, pending))
            }
        }
    }
}

/**
 * The pairing code in a soft capsule with a countdown ring of the minutes it still works (of 15),
 * or the "expired" note; and New code. A new code's characters arrive one by one.
 */
@Composable
private fun PairingBlock(state: SettingsUiState, actions: SettingsActions, busy: Boolean) {
    val c = Ember.colors
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BlockLabel(stringResource(R.string.cloud_code_title))
        val code = state.cloudPairingCode
        val now = System.currentTimeMillis()
        if (code != null && code.second > now) {
            val minutes = ((code.second - now) / 60_000L + 1).toInt()
            PairingCode(code.first)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ring(
                    value = minutes.coerceAtMost(CodeLifetimeMinutes).toFloat(),
                    target = CodeLifetimeMinutes.toFloat(),
                    size = 44.dp,
                    stroke = 5.dp,
                ) {
                    BasicText(
                        minutes.toString(),
                        style = Ember.type.rowNumber.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = { c.label },
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                GroupText(stringResource(R.string.cloud_code_hint, minutes), Modifier.weight(1f))
            }
        } else if (code != null) {
            GroupText(stringResource(R.string.cloud_code_expired))
        }
        EmberButton(
            stringResource(R.string.cloud_code_new),
            onClick = actions::newPairingCode,
            variant = ButtonVariant.Fill,
            icon = EmberIcons.Refresh,
            enabled = !busy,
        )
    }
}

/** How long a pairing code works: the countdown ring is full at this many minutes. */
private const val CodeLifetimeMinutes = 15

/**
 * The code itself, 30 sp tabular with wide tracking in a `surface2` capsule. When a new code replaces
 * the shown one, its characters rise in one after another (never on the first composition).
 */
@Composable
private fun PairingCode(code: String) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    var shown by remember { mutableStateOf(code) }
    val arrive = remember(code) { code != shown && !reduced }
    SideEffect { shown = code }
    // Each character is a text of its own (so it can rise on its own), and a text's letter spacing
    // is not drawn after its last glyph: the 0.12 em tracking is a gap between the characters instead.
    val style = Ember.type.stat.copy(fontSize = 30.sp, letterSpacing = 0.sp, color = c.label)
    // 0.12 of the size the text is drawn at: large text sizes scale 30 sp less than they scale 3.6 sp.
    val tracking = with(LocalDensity.current) { (style.fontSize.toPx() * .12f).toDp() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, EmberShapes.capsule)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = code },
    ) {
        code.forEachIndexed { i, ch ->
            val p = remember(code) { Animatable(if (arrive) 0f else 1f) }
            LaunchedEffect(code) {
                if (p.value < 1f) p.animateTo(1f, tween(560, 40 * i, EmberEasing.Snappy))
            }
            BasicText(
                ch.toString(),
                style = style,
                modifier = Modifier.padding(end = if (i < code.lastIndex) tracking else 0.dp).graphicsLayer {
                    alpha = p.value.coerceIn(0f, 1f)
                    translationY = (1f - p.value) * 16.dp.toPx()
                },
            )
        }
    }
}

/** The error codes CloudSyncWorker records, in the user's words. */
@Composable
private fun cloudErrorText(code: String): String = stringResource(
    when (code) {
        "offline" -> R.string.cloud_error_offline
        "auth" -> R.string.cloud_error_auth
        "server" -> R.string.cloud_error_server
        "rejected" -> R.string.cloud_error_rejected
        else -> R.string.cloud_error_failed
    },
)

/**
 * Health Connect diagnostics (only when it is installed: its settings intent would resolve nowhere
 * otherwise): when it last synced, and the way to its data sources.
 */
@Composable
private fun HealthConnectSection(state: SettingsUiState, modifier: Modifier) {
    val c = Ember.colors
    val context = LocalContext.current
    Section(stringResource(R.string.hc_section), modifier) {
        val time = state.lastSyncEpochMillis?.let { formattedDateTime(it) } ?: stringResource(R.string.no_data_dash)
        val (title, value) = labelAndValue(R.string.last_synced, time)
        InsetGroup {
            row {
                // The time sits on the right while the words and the time both fit on one line. When
                // they do not (large text, a long date format), it moves under the words, so the title
                // keeps its natural width instead of being squeezed into broken pieces.
                BoxWithConstraints {
                    val measurer = rememberTextMeasurer()
                    val style = Ember.type.callout
                    val besideFits = value == null || with(LocalDensity.current) {
                        val words = measurer.measure(title, style, maxLines = 1).size.width
                        val time = measurer.measure(value, style, maxLines = 1).size.width
                        // The row's 16 dp padding on each side and the 12 dp between title and trailing.
                        words + time + (16.dp * 2 + 12.dp).roundToPx() <= constraints.maxWidth
                    }
                    ListRow(
                        title = title,
                        subtitle = value.takeUnless { besideFits },
                        trailing = value?.takeIf { besideFits }?.let { v ->
                            { BasicText(v, style = style, color = { c.label2 }, maxLines = 1) }
                        },
                    )
                }
            }
            row {
                ListRow(
                    title = stringResource(R.string.hc_open_settings),
                    titleColor = c.tint,
                    chevron = true,
                    onClick = { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) },
                )
            }
        }
    }
}

/**
 * "Last synced: 10/10/26, 16:52" as a row: the words as the title and the time on the right. The
 * split is at the placeholder, so it works in every language whose sentence ends with the value;
 * otherwise the whole sentence is the title.
 */
@Composable
private fun labelAndValue(res: Int, value: String): Pair<String, String?> {
    val marker = "\u0000"
    val whole = stringResource(res, marker)
    return if (whole.endsWith(marker)) {
        whole.removeSuffix(marker).trimEnd().trimEnd(':').trimEnd() to value
    } else {
        stringResource(res, value) to null
    }
}

/** Reset: a group of its own at the very end, one danger row; it asks first. */
@Composable
private fun ResetSection(actions: SettingsActions, modifier: Modifier) {
    val c = Ember.colors
    Section(header = null, modifier = modifier) {
        InsetGroup {
            row {
                ListRow(
                    title = stringResource(R.string.reset_button),
                    leading = { EmberIcon(EmberIcons.Trash, null, size = 20.dp, tint = c.danger) },
                    titleColor = c.danger,
                    onClick = { actions.setShowResetDialog(true) },
                )
            }
        }
    }
}

// ---------- Sheets ----------

/** Asked once, when the switch is turned on without a stored name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudNameSheet(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val valid = name.trim().length in 1..40
    val closer = rememberSheetCloser()
    val title = stringResource(R.string.cloud_name_dialog_title)
    val cancel = stringResource(R.string.cancel)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        // Cancel and Turn on are the sheet's pair, so no close button: one way to cancel, read once.
        SheetHeader(title)
        GroupText(stringResource(R.string.cloud_name_hint))
        EmberTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = stringResource(R.string.cloud_name_label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        SheetButtonPair(
            modifier = Modifier.padding(top = 8.dp),
            cancel = {
                EmberButton(cancel, { closer.close(onDismiss) }, variant = ButtonVariant.Fill, size = ButtonSize.Lg)
            },
            confirm = {
                EmberButton(
                    stringResource(R.string.cloud_switch_on),
                    onClick = { closer.close { onConfirm(name.trim()) } },
                    size = ButtonSize.Lg,
                    enabled = valid,
                )
            },
        )
    }
}

/** Pick a workout, an amount and the weekdays it repeats on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringSheet(
    onConfirm: (WorkoutType, Int, Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(WorkoutType.TREADMILL_WALK) }
    var amountText by rememberSaveable { mutableStateOf("30") }
    var days by rememberSaveable { mutableStateOf(setOf<DayOfWeek>()) }

    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    val valid = amount != null && days.isNotEmpty()
    val closer = rememberSheetCloser()
    val title = stringResource(R.string.recurring_dialog_title)

    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        SheetHeader(title, onClose = { closer.close(onDismiss) }, closeLabel = stringResource(R.string.cancel))

        val types = WorkoutType.entries
        val typeLabel = stringResource(R.string.workout_type_label)
        Column {
            BlockLabel(typeLabel, Modifier.padding(start = 4.dp, bottom = 4.dp))
            ChipGroup(
                options = types.map { stringResource(workoutTypeLabel(it)) },
                selected = setOf(types.indexOf(selected)),
                onToggle = { i ->
                    val type = types[i]
                    // Only reset the amount when the input UNIT changes (minutes <-> repetitions).
                    if (type.kind != selected.kind) {
                        amountText = if (type.kind == WorkoutKind.DURATION) "30" else "20"
                    }
                    selected = type
                },
                groupLabel = typeLabel,
            )
        }

        val duration = selected.kind == WorkoutKind.DURATION
        val fieldLabel = stringResource(if (duration) R.string.workout_minutes_label else R.string.workout_reps_label)
        Column {
            BlockLabel(fieldLabel, Modifier.padding(start = 4.dp, bottom = 4.dp))
            // Minutes step by 5 and reps by 1, landing on the step's grid (32 → 35 → 40).
            val step = if (duration) 5 else 1
            val base = amount ?: if (duration) 30 else 20
            AmountStepper(
                text = amountText,
                onTextChange = { amountText = it },
                unit = if (duration) stringResource(R.string.form_unit_min) else pluralStringResource(R.plurals.form_unit_reps, base),
                onDecrease = { amountText = (((base - 1) / step) * step).coerceIn(1, 999).toString() },
                onIncrease = { amountText = ((base / step + 1) * step).coerceIn(1, 999).toString() },
                fieldLabel = fieldLabel,
                decreaseLabel = stringResource(R.string.amount_less),
                increaseLabel = stringResource(R.string.amount_more),
                isError = amountText.isNotBlank() && amount == null,
                keyboardType = KeyboardType.Number,
            )
        }

        val daysLabel = stringResource(R.string.recurring_days_label)
        Column {
            BlockLabel(daysLabel, Modifier.padding(start = 4.dp, bottom = 4.dp))
            val week = DayOfWeek.entries
            val locale = LocalConfiguration.current.locales[0]
            ChipGroup(
                options = week.map { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) },
                selected = week.indices.filter { week[it] in days }.toSet(),
                onToggle = { i ->
                    val day = week[i]
                    days = if (day in days) days - day else days + day
                },
                multiSelect = true,
                groupLabel = daysLabel,
            )
        }

        EmberButton(
            stringResource(R.string.add_action),
            onClick = { amount?.let { a -> closer.close { onConfirm(selected, a, days) } } },
            modifier = Modifier.padding(top = 4.dp),
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
            enabled = valid,
        )
    }
}

/** The 24-hour Material time picker in an Ember sheet, in the Ember colours, with Cancel and Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeSheet(
    slot: MealSlot,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ember.colors
    val timeState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    val closer = rememberSheetCloser()
    val title = stringResource(R.string.reminder_time_title)
    val cancel = stringResource(R.string.cancel)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        // Cancel and Save below are the way out (with drag, the scrim and back), so no close button.
        SheetHeader(title, subtitle = mealSlotLabel(slot))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            // The dial is a fixed size: past 1.5× its numbers would crowd each other, so its own text
            // stops growing there (the sheet's title and buttons keep the full scale).
            val outer = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(outer.density, outer.fontScale.coerceAtMost(1.5f))) {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.fill,
                        clockDialSelectedContentColor = c.onInk,
                        clockDialUnselectedContentColor = c.label,
                        selectorColor = c.ink,
                        containerColor = Color.Transparent,
                        periodSelectorBorderColor = c.sep,
                        periodSelectorSelectedContainerColor = c.ink,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = c.onInk,
                        periodSelectorUnselectedContentColor = c.label,
                        timeSelectorSelectedContainerColor = c.ink,
                        timeSelectorUnselectedContainerColor = c.fill2,
                        timeSelectorSelectedContentColor = c.onInk,
                        timeSelectorUnselectedContentColor = c.label,
                    ),
                )
            }
        }
        SheetButtonPair(
            cancel = {
                EmberButton(cancel, { closer.close(onDismiss) }, variant = ButtonVariant.Fill, size = ButtonSize.Lg)
            },
            confirm = {
                EmberButton(
                    stringResource(R.string.save),
                    onClick = { closer.close { onConfirm(timeState.hour * 60 + timeState.minute) } },
                    size = ButtonSize.Lg,
                )
            },
        )
    }
}

// ---------- Small local pieces ----------

/**
 * A secret (the AI key, the bot token): masked unless shown with the eye button. A password field is
 * not only about the dots: it is what stops the keyboard from learning the key and later suggesting
 * it inside other apps, and what stops autocorrect from quietly mangling it.
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String,
    enabled: Boolean = true,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    EmberTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isError = isError,
        errorText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            EmberIconButton(
                if (revealed) EmberIcons.EyeOff else EmberIcons.Eye,
                stringResource(if (revealed) R.string.ai_key_hide else R.string.ai_key_show),
                onClick = { revealed = !revealed },
                style = IconButtonStyle.Plain,
                size = 40.dp,
                enabled = enabled,
            )
        },
        enabled = enabled,
    )
}

/** Save, Delete and a result side by side under a field; they wrap at large text sizes. */
@Composable
private fun ActionLine(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * "Delete", "Unlink", "Remove": the danger text button (17 sp Medium, no fill), 48 dp to the finger,
 * dimmed and inert while [enabled] is false (a Telegram or cloud call is running).
 */
@Composable
private fun DangerLink(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .heightIn(min = EmberSpace.TouchTarget)
            .widthIn(min = EmberSpace.TouchTarget)
            .graphicsLayer {
                alpha = when {
                    !enabled -> .38f
                    pressed -> .55f
                    else -> 1f
                }
            }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.field, c.focus),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Ember.type.body.copy(fontWeight = FontWeight.Medium), color = { c.danger }, maxLines = 1)
    }
}

/** "✓ Key saved": a good-coloured result next to the buttons, announced when it appears. */
@Composable
private fun SavedMark(text: String) {
    val c = Ember.colors
    Row(
        Modifier
            .padding(start = 4.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EmberIcon(EmberIcons.Check, null, size = 16.dp, tint = c.good)
        BasicText(text, style = Ember.type.subhead, color = { c.good })
    }
}

/** "+ Add regular activity": a tint row with a plus glyph where the other rows have their tile. */
@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    // The glyph draws the "+" the string starts with, so the label does not repeat it.
    TintRow(text = text.removePrefix("+").trim(), icon = EmberIcons.Plus, onClick = onClick)
}

/** A tint action row ("Send a test message"); while [busy] it shows the spinner and waits. */
@Composable
private fun TintRow(text: String, icon: EmberIcons, onClick: () -> Unit, busy: Boolean = false) {
    val c = Ember.colors
    ListRow(
        title = text,
        leading = {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                EmberIcon(icon, null, size = 20.dp, tint = c.tint)
            }
        },
        trailing = if (busy) ({ EmberSpinner(20.dp) }) else null,
        onClick = if (busy) null else onClick,
        titleColor = c.tint,
    )
}

/** A 30 dp `fill` tile with a glyph, at the start of a row (regular activities). */
@Composable
private fun IconTile(icon: EmberIcons) {
    val c = Ember.colors
    Box(Modifier.size(30.dp).background(c.fill, EmberShapes.rowIcon), contentAlignment = Alignment.Center) {
        EmberIcon(icon, null, size = 18.dp, tint = c.label)
    }
}

/**
 * A person with an avatar ("Partner: Olena" / "via the bot", or someone who can see the day) and a
 * danger [action] (Unlink, Remove) at the end; at large text sizes the action moves under the name,
 * so neither the name nor the bot's handle is squeezed. The texts read as one TalkBack stop.
 */
@Composable
private fun PersonRow(name: String, title: String, subtitle: String? = null, action: @Composable () -> Unit) {
    val c = Ember.colors
    val stacked = LocalDensity.current.fontScale >= StackFontScale
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = EmberSpace.RowMinHeight)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(name)
        Column(Modifier.weight(1f)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                WholeWordsText(title, Ember.type.callout.copy(color = c.label))
                if (subtitle != null) BasicText(subtitle, style = Ember.type.footnote, color = { c.label2 })
            }
            if (stacked) {
                // The link's own padding would push its text off the name's edge: pull it back.
                Box(Modifier.offset(x = (-8).dp)) { action() }
            }
        }
        if (!stacked) action()
    }
}

/** A partner's round avatar: the initial in white on the soft indigo of the dinner tile. */
@Composable
private fun Avatar(name: String) {
    val c = Ember.colors
    Box(Modifier.size(30.dp).background(MealTiles.Dinner, EmberShapes.circle), contentAlignment = Alignment.Center) {
        BasicText(
            name.trim().take(1).uppercase(),
            style = Ember.type.subhead.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = { c.onAccent },
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** An explanation above a group ("Let someone you trust follow…"), in the footer's voice. */
@Composable
private fun GroupIntro(text: String) {
    val c = Ember.colors
    BasicText(
        text,
        style = Ember.type.footnote.copy(lineHeight = 18.sp),
        color = { c.label2 },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** A footnote inside a group (hints, the empty text, the pending line). */
@Composable
private fun GroupText(text: String, modifier: Modifier = Modifier) {
    val c = Ember.colors
    BasicText(text, style = Ember.type.footnote.copy(lineHeight = 18.sp), color = { c.label2 }, modifier = modifier)
}

/** A row that is only a note ("None yet — …"). */
@Composable
private fun GroupNote(text: String) {
    GroupText(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp))
}

/** A timestamp in the phone's own short date + time format. */
@Composable
private fun formattedDateTime(epochMillis: Long): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}
