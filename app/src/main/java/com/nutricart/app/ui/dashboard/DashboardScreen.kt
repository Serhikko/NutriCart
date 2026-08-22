package com.nutricart.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.dashboard.cards.ActivityCard
import com.nutricart.app.ui.dashboard.cards.HEALTH_CONNECT_PLAY_URL
import com.nutricart.app.ui.dashboard.cards.HcBannerCard
import com.nutricart.app.ui.dashboard.cards.HeroRing
import com.nutricart.app.ui.dashboard.cards.MacroCard
import com.nutricart.app.ui.dashboard.cards.TodayMenuCard
import com.nutricart.app.ui.dashboard.cards.WaterCard
import com.nutricart.app.ui.dashboard.cards.WeightCard
import com.nutricart.app.ui.stats.StatsBody
import com.nutricart.app.ui.stats.StatsRange

/**
 * The four horizons of this screen. One question — "how am I doing" — over
 * one day or over a stretch of days, so TODAY shows the dashboard and the
 * other three show the statistics body. [statsRange] is null for TODAY only.
 */
enum class TodayRange(val labelRes: Int, val statsRange: StatsRange?) {
    TODAY(R.string.tab_today, null),
    WEEK(R.string.stats_range_week, StatsRange.WEEK),
    MONTH(R.string.stats_range_month, StatsRange.MONTH),
    NINETY(R.string.stats_range_90, StatsRange.NINETY),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var range by rememberSaveable { mutableStateOf(TodayRange.TODAY) }
    val bodyState = rememberSaveableStateHolder()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            RangeChips(selected = range, onSelect = { range = it })

            // The body is SWAPPED, never appended: this screen can hold four
            // horizons without ever growing into an endless scroll.
            // weight(1f), not fillMaxSize: the body must take what is LEFT
            // under the chip row, not the whole screen height.
            Box(modifier = Modifier.weight(1f)) {
                val statsRange = range.statsRange
                // Swapping the body would otherwise throw away its scroll
                // position and its own inner state. One saved slot per body
                // (today / statistics) keeps both where the user left them,
                // exactly as separate tabs used to.
                bodyState.SaveableStateProvider(key = statsRange == null) {
                    if (statsRange == null) {
                        DashboardBody(
                            state = state,
                            viewModel = viewModel,
                            onOpenRecipe = onOpenRecipe,
                            onGrantPermissions = {
                                permissionLauncher.launch(viewModel.healthPermissions)
                            },
                        )
                    } else {
                        StatsBody(statsRange)
                    }
                }
            }
        }
    }
}

/** Today / Week / Month / 90 days. Scrolls sideways on narrow phones. */
@Composable
private fun RangeChips(selected: TodayRange, onSelect: (TodayRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TodayRange.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

/** Everything about TODAY: the ring, the cards and the pull-to-refresh sync. */
@Composable
private fun DashboardBody(
    state: DashboardUiState,
    viewModel: DashboardViewModel,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onGrantPermissions: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
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
                            onGrant = onGrantPermissions,
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

                // "Last synced" and the Health Connect settings shortcut used
                // to sit here; they are diagnostics, not today's numbers, and
                // now live in Settings > Health Connect.
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
