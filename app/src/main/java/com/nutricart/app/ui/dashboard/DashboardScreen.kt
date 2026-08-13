package com.nutricart.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.Color
import com.nutricart.app.R
import com.nutricart.app.domain.logic.NutrientTargets
import com.nutricart.app.domain.logic.WorkoutMath
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.AnimatedNumber
import com.nutricart.app.ui.common.CalorieRing
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.MacroBar
import com.nutricart.app.ui.common.WeightChart
import com.nutricart.app.ui.common.hcExerciseLabel
import com.nutricart.app.ui.common.workoutTypeLabel
import com.nutricart.app.ui.diary.mealSlotLabel
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val HEALTH_CONNECT_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync every time the screen comes to the foreground (first open included).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // System dialog for Health Connect permissions.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (viewModel.hasRequiredHealthPermissions(granted)) {
            viewModel.refresh()
        } else {
            // The result lists every CURRENTLY granted permission, not just the
            // newly granted ones — so "is it empty" is the wrong question: with
            // only an optional type granted (say, sleep) the set is non-empty,
            // yet sync still can't run. When the two REQUIRED permissions are
            // still missing, the dialog was denied (or blocked after two
            // denials and returned instantly) — the Health Connect settings
            // screen is the only remaining place where they can be granted.
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }
    }

    // One-shot error message when a sync attempt failed.
    val syncFailedMessage = stringResource(R.string.sync_failed)
    LaunchedEffect(state.syncFailed) {
        if (state.syncFailed) {
            snackbarHostState.showSnackbar(syncFailedMessage)
            viewModel.clearSyncFailed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.loading) {
                LoadingBox()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Freeze the banner content during its exit animation:
                    // while it shrinks away the state is already NONE, which
                    // would otherwise flash the wrong text for a moment.
                    var shownBanner by remember { mutableStateOf(state.hcBanner) }
                    if (state.hcBanner != HcBannerState.NONE) shownBanner = state.hcBanner

                    // Banners slide in and out instead of popping.
                    AnimatedVisibility(
                        visible = state.hcBanner != HcBannerState.NONE,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            HcBannerCard(
                                banner = shownBanner,
                                onInstallOrUpdate = { uriHandler.openUri(HEALTH_CONNECT_PLAY_URL) },
                                onGrant = { permissionLauncher.launch(viewModel.healthPermissions) },
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    AnimatedVisibility(
                        visible = state.showNoDataHint,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            Card {
                                Text(
                                    stringResource(R.string.hc_no_data_hint),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    HeroRing(state)

                    if (state.streakDays >= 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.streak_value, state.streakDays),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    if (state.todayMenu.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        TodayMenuCard(menu = state.todayMenu, onOpenRecipe = onOpenRecipe)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    MacroCard(state)

                    Spacer(modifier = Modifier.height(16.dp))
                    WaterCard(
                        waterMl = state.waterMl,
                        onAdd = viewModel::addWater,
                        onUndo = viewModel::undoWater,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    var showAddWorkout by rememberSaveable { mutableStateOf(false) }
                    ActivityCard(
                        state = state,
                        onAddWorkout = { showAddWorkout = true },
                        onDeleteWorkout = viewModel::deleteWorkout,
                    )
                    if (showAddWorkout) {
                        AddWorkoutDialog(
                            weightKg = state.weightKg,
                            onConfirm = { type, amount ->
                                viewModel.addWorkout(type, amount)
                                showAddWorkout = false
                            },
                            onDismiss = { showAddWorkout = false },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    WeightCard(state)

                    // Numbers look wrong (e.g. watch steps missing)? Let the
                    // user inspect Health Connect's own sources and priorities.
                    // Only when HC is actually usable — with no HC installed
                    // this intent would resolve nowhere and crash.
                    if (state.hcBanner == HcBannerState.NONE) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                                )
                            },
                        ) {
                            Text(stringResource(R.string.hc_open_settings))
                        }
                    }

                    state.lastSyncEpochMillis?.let { millis ->
                        Spacer(modifier = Modifier.height(12.dp))
                        LastSyncedText(millis)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.dashboard_placeholder_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** The centerpiece: animated ring + rolling "remaining" counter + key stats. */
@Composable
private fun HeroRing(state: DashboardUiState) {
    val targets = state.targets ?: return
    val overTarget = state.remainingKcal < 0

    Box(contentAlignment = Alignment.Center) {
        CalorieRing(
            progress = if (targets.kcal > 0) state.eatenKcal / targets.kcal.toFloat() else 0f,
            modifier = Modifier.size(240.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedNumber(
                    value = state.remainingKcal,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (overTarget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.kcal_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.remaining_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HeroStat(stringResource(R.string.eaten_label), state.eatenKcal)
        HeroStat(stringResource(R.string.dashboard_target), targets.kcal)
        HeroStat(stringResource(R.string.calories_out_label), state.caloriesOut)
    }

    // One note, most useful first: "+N kcal" covers watch AND manual workouts;
    // the old watch note stays for the rare measured-zero day (bonus == 0 but
    // the formula did switch to watch mode).
    if (state.activityBonusKcal > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.target_activity_bonus, state.activityBonusKcal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    } else if (state.adjustedByActivity) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.target_adjusted_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun HeroStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value = value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroCard(state: DashboardUiState) {
    val targets = state.targets ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // "Green numbers": the pair turns green inside the 90-110% band.
            MacroBar(
                stringResource(R.string.summary_protein), state.eatenProteinG, targets.proteinG,
                valueColor = targetStateColor(state.eatenProteinG.toDouble(), targets.proteinG),
            )
            MacroBar(
                stringResource(R.string.summary_fat), state.eatenFatG, targets.fatG,
                valueColor = targetStateColor(state.eatenFatG.toDouble(), targets.fatG),
            )
            MacroBar(
                stringResource(R.string.summary_carbs), state.eatenCarbsG, targets.carbsG,
                valueColor = targetStateColor(state.eatenCarbsG.toDouble(), targets.carbsG),
            )
            MacroBar(
                stringResource(R.string.nutrient_fiber), state.eatenFiberG.roundToInt(), state.fiberTargetG,
                valueColor = targetStateColor(state.eatenFiberG, state.fiberTargetG),
            )

            HorizontalDivider()
            // Limits work the other way around: green means UNDER the number.
            LimitRow(R.string.nutrient_sugars, state.eatenSugarsG, state.sugarLimitG)
            LimitRow(R.string.nutrient_salt, state.eatenSaltG, state.saltLimitG)
            LimitRow(R.string.nutrient_sat_fat, state.eatenSatFatG, state.satFatLimitG)
        }
    }
}

/** Color for a "reach the target" nutrient: neutral -> green (in band) -> red. */
@Composable
private fun targetStateColor(eatenG: Double, targetG: Int): Color =
    when (NutrientTargets.targetState(eatenG, targetG)) {
        NutrientTargets.State.GOOD -> MaterialTheme.colorScheme.primary
        NutrientTargets.State.OVER -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** One "stay under the limit" line: green under, amber near, red over. */
@Composable
private fun LimitRow(labelRes: Int, eatenG: Double, limitG: Int) {
    val color = when (NutrientTargets.limitState(eatenG, limitG.toDouble())) {
        NutrientTargets.State.GOOD -> MaterialTheme.colorScheme.primary
        NutrientTargets.State.WARN -> MaterialTheme.colorScheme.tertiary
        NutrientTargets.State.OVER -> MaterialTheme.colorScheme.error
        NutrientTargets.State.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.nutrient_vs_limit, eatenG, limitG),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun ActivityCard(
    state: DashboardUiState,
    onAddWorkout: () -> Unit,
    onDeleteWorkout: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueRow(
                labelRes = R.string.steps_label,
                value = state.steps?.toString() ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.active_kcal_label,
                value = state.activeKcal
                    ?.let { stringResource(R.string.kcal_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.exercise_label,
                value = state.exerciseMinutes
                    ?.let { stringResource(R.string.minutes_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.sleep_label,
                value = state.sleepMinutes
                    ?.let { stringResource(R.string.sleep_value, it / 60, it % 60) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.heart_rate_label,
                value = state.avgHeartRateBpm
                    ?.let { stringResource(R.string.bpm_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.workouts_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onAddWorkout) {
                    Text(stringResource(R.string.workout_add))
                }
            }
            if (state.workouts.isEmpty()) {
                Text(
                    stringResource(R.string.workouts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.workouts.forEach { item ->
                    WorkoutRow(item = item, onDelete = onDeleteWorkout)
                }
            }

            HorizontalDivider()
            ValueRow(
                labelRes = R.string.dashboard_current_weight,
                value = stringResource(R.string.weight_kg_value, state.weightKg),
            )
        }
    }
}

/** One workout line: name + amount/source underneath, kcal and (for manual) delete. */
@Composable
private fun WorkoutRow(item: WorkoutItem, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Manual rows have a catalog type; watch rows use their own title,
            // or a name mapped from the raw HC exercise type when there is none.
            val name = when {
                item.type != null -> stringResource(workoutTypeLabel(item.type))
                !item.title.isNullOrBlank() -> item.title
                else -> stringResource(hcExerciseLabel(item.hcExerciseType))
            }
            Text(name, style = MaterialTheme.typography.bodyLarge)
            val amountText = item.minutes?.let { stringResource(R.string.minutes_value, it) }
                ?: item.reps?.let { stringResource(R.string.workout_reps_value, it) }
            val sourceText = stringResource(
                if (item.isFromWatch) R.string.workout_source_watch
                else R.string.workout_source_manual
            )
            Text(
                listOfNotNull(amountText, sourceText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            item.kcal?.let { stringResource(R.string.kcal_value, it) }
                ?: stringResource(R.string.no_data_dash),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!item.isFromWatch) {
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.workout_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Type picker (chips) + one number field. The field means minutes or
 * repetitions depending on the chosen type, and the kcal preview uses the
 * same WorkoutMath the repository will store — no surprises after saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddWorkoutDialog(
    weightKg: Double,
    onConfirm: (WorkoutType, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: the picked type and amount survive screen rotation.
    var selected by rememberSaveable { mutableStateOf(WorkoutType.RUNNING) }
    var amountText by rememberSaveable { mutableStateOf("30") }

    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    val previewKcal = amount?.let {
        when (selected.kind) {
            WorkoutKind.DURATION -> WorkoutMath.kcalForDuration(selected, weightKg, it)
            WorkoutKind.REPS -> WorkoutMath.kcalForReps(selected, weightKg, it)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutType.entries.forEach { type ->
                        FilterChip(
                            selected = type == selected,
                            onClick = {
                                // Only reset the amount when the input UNIT
                                // changes (minutes <-> repetitions).
                                if (type.kind != selected.kind) {
                                    amountText =
                                        if (type.kind == WorkoutKind.DURATION) "30" else "20"
                                }
                                selected = type
                            },
                            label = { Text(stringResource(workoutTypeLabel(type))) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            stringResource(
                                if (selected.kind == WorkoutKind.DURATION) R.string.workout_minutes_label
                                else R.string.workout_reps_label
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (previewKcal != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.kcal_value, previewKcal.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.workout_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { amount?.let { onConfirm(selected, it) } },
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Today's planned meals — one tap away from the recipe. */
@Composable
private fun TodayMenuCard(
    menu: List<TodayMenuItem>,
    onOpenRecipe: (Long, Double) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.today_menu_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            menu.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRecipe(item.recipeId, item.portionFactor) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mealSlotLabel(item.slot),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        stringResource(R.string.kcal_value, item.kcal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Water: today's total (rolling number) + quick-add and undo buttons. */
@Composable
private fun WaterCard(waterMl: Int, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.water_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(
                        value = waterMl,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " " + stringResource(R.string.ml_unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAdd(250) }) {
                    Text(stringResource(R.string.water_add_250))
                }
                FilledTonalButton(onClick = { onAdd(500) }) {
                    Text(stringResource(R.string.water_add_500))
                }
                TextButton(onClick = onUndo, enabled = waterMl > 0) {
                    Text(stringResource(R.string.water_undo))
                }
            }
        }
    }
}

/** Weight: the history chart with a dashed trend line and kg/week slope. */
@Composable
private fun WeightCard(state: DashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.weight_card_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.weightTrend?.let { trend ->
                    Text(
                        stringResource(R.string.weight_trend_value, trend.slopeKgPerWeek),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.weightPoints.size >= 2) {
                WeightChart(
                    points = state.weightPoints,
                    trend = state.weightTrend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                val dateFormatter = remember {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        LocalDate.ofEpochDay(state.weightPoints.first().first).format(dateFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        LocalDate.ofEpochDay(state.weightPoints.last().first).format(dateFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.weight_chart_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LastSyncedText(epochMillis: Long) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val text = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
    Text(
        stringResource(R.string.last_synced, text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One card per Health Connect problem, each with the action that fixes it. */
@Composable
private fun HcBannerCard(
    banner: HcBannerState,
    onInstallOrUpdate: () -> Unit,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val textRes = when (banner) {
                HcBannerState.NOT_INSTALLED -> R.string.hc_banner_not_installed
                HcBannerState.UPDATE_REQUIRED -> R.string.hc_banner_update
                else -> R.string.hc_banner_no_permission
            }
            Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium)

            when (banner) {
                HcBannerState.NOT_INSTALLED -> Button(onClick = onInstallOrUpdate) {
                    Text(stringResource(R.string.hc_install))
                }
                HcBannerState.UPDATE_REQUIRED -> Button(onClick = onInstallOrUpdate) {
                    Text(stringResource(R.string.hc_update))
                }
                else -> Button(onClick = onGrant) {
                    Text(stringResource(R.string.hc_grant))
                }
            }
        }
    }
}

@Composable
private fun ValueRow(labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
