package com.nutricart.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.ui.common.AnimatedNumber
import com.nutricart.app.ui.common.CalorieRing
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.MacroBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val HEALTH_CONNECT_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
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
        if (granted.isEmpty()) {
            // After two denials Android blocks this dialog and returns instantly
            // with nothing granted — send the user to the Health Connect
            // settings screen instead of leaving a button that "does nothing".
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } else {
            viewModel.refresh()
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

                    Spacer(modifier = Modifier.height(20.dp))
                    MacroCard(state)

                    Spacer(modifier = Modifier.height(16.dp))
                    ActivityCard(state)

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

    if (state.adjustedByActivity) {
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
            MacroBar(stringResource(R.string.summary_protein), state.eatenProteinG, targets.proteinG)
            MacroBar(stringResource(R.string.summary_fat), state.eatenFatG, targets.fatG)
            MacroBar(stringResource(R.string.summary_carbs), state.eatenCarbsG, targets.carbsG)
        }
    }
}

@Composable
private fun ActivityCard(state: DashboardUiState) {
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
            ValueRow(
                labelRes = R.string.dashboard_current_weight,
                value = stringResource(R.string.weight_kg_value, state.weightKg),
            )
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
