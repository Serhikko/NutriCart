package com.nutricart.app.ui.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.nutricart.app.ui.ember.EmberSpace
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nutricart.app.ui.ember.rememberGutter
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.dashboard.cards.ActivityTiles
import com.nutricart.app.ui.dashboard.cards.HEALTH_CONNECT_PLAY_URL
import com.nutricart.app.ui.dashboard.cards.HcBannerCard
import com.nutricart.app.ui.dashboard.cards.HeroRing
import com.nutricart.app.ui.dashboard.cards.MacroCard
import com.nutricart.app.ui.dashboard.cards.NoDataHint
import com.nutricart.app.ui.dashboard.cards.TodayMenuCard
import com.nutricart.app.ui.dashboard.cards.WaterCard
import com.nutricart.app.ui.dashboard.cards.WeightCard
import com.nutricart.app.ui.dashboard.cards.WorkoutsCard
import com.nutricart.app.ui.dashboard.cards.cappedAt
import com.nutricart.app.ui.dashboard.cards.rememberLongDate
import com.nutricart.app.ui.dashboard.cards.rememberPeriodLabel
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRing
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberHeroRingSize
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.navigation.LocalAddedHandOff
import com.nutricart.app.ui.stats.StatsBody
import com.nutricart.app.ui.stats.StatsRange
import kotlinx.coroutines.launch
import java.time.LocalDate

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

@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
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
            snackbarHostState.showSnackbar(EmberToastVisuals(syncFailedMessage, ToastIcon.Warning))
            viewModel.clearSyncFailed()
        }
    }

    DashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenSettings = onOpenSettings,
        onOpenRecipe = onOpenRecipe,
        onRefresh = viewModel::refresh,
        onGrantPermissions = { permissionLauncher.launch(viewModel.healthPermissions) },
        onAddWater = viewModel::addWater,
        onUndoWater = viewModel::undoWater,
        onAddWorkout = viewModel::addWorkout,
        onDeleteWorkout = viewModel::deleteWorkout,
        statsBody = { statsRange -> StatsBody(statsRange) },
    )
}

/**
 * The stateless half of [DashboardScreen]: everything it draws, driven only by
 * [state] and callbacks (screenshot tests render this directly). [statsBody]
 * draws the Week / Month / 90-day horizons; [initialRange] is the segment that is
 * selected when the screen first appears.
 *
 * One Ember page for all four horizons: the large title "Today" with the date (or the period) above
 * it, the streak and the Settings gear beside it and the Today · Week · Month · 90 days control below
 * it, so switching horizons swaps only the body under a header that stays put (its thumb slides).
 * Each body keeps its own scroll position. Pull to refresh syncs Health Connect.
 */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onRefresh: () -> Unit,
    onGrantPermissions: () -> Unit,
    onAddWater: (ml: Int) -> Unit,
    onUndoWater: () -> Unit,
    onAddWorkout: (type: WorkoutType, amount: Int) -> Unit,
    onDeleteWorkout: (id: Long) -> Unit,
    statsBody: @Composable (StatsRange) -> Unit,
    initialRange: TodayRange = TodayRange.TODAY,
) {
    var range by rememberSaveable { mutableStateOf(initialRange) }
    var showAddWorkout by rememberSaveable { mutableStateOf(false) }
    // The body is SWAPPED, never appended: this screen holds four horizons without growing into an
    // endless scroll. Each body keeps where the user left it, as separate tabs used to.
    val todayList = rememberLazyListState()
    val statsList = rememberLazyListState()
    // Asked only while the Today body shows: opening on Week and switching to Today later still gets
    // the day's first build. Each visit to the body opens its own entrance window (the cards rise on
    // the first open, fade up calmly when switching back).
    val showingToday = range == TodayRange.TODAY
    val first = if (showingToday) rememberFirstOpen("today") else false
    val entrances = key(showingToday) { rememberEntranceState() }
    // Read on every composition: after midnight the next refresh rolls the screen to the new day.
    val today = LocalDate.now()

    AddedToast(snackbarHostState)

    val title = stringResource(R.string.dashboard_title)
    val statsRange = range.statsRange
    val eyebrow = if (statsRange == null) {
        rememberLongDate(today)
    } else {
        rememberPeriodLabel(today.minusDays(statsRange.days - 1L), today)
    }
    val compactTitle = compactTitle(title, state, range)
    val uriHandler = LocalUriHandler.current

    LargeTitleScaffold(
        title = title,
        eyebrow = eyebrow,
        actions = {
            if (state.streakDays >= 2) StreakChip(state.streakDays, room = streakRoom(title))
            EmberIconButton(
                EmberIcons.Settings,
                stringResource(R.string.settings_title),
                onOpenSettings,
                style = IconButtonStyle.Surface,
            )
        },
        below = {
            SegmentedControl(
                options = TodayRange.entries.map { stringResource(it.labelRes) },
                selectedIndex = range.ordinal,
                onSelect = { range = TodayRange.entries[it] },
                role = SegmentRole.Tab,
            )
        },
        compactTitle = compactTitle,
        toastHostState = snackbarHostState,
        listState = if (statsRange == null) todayList else statsList,
        // Pulling syncs Health Connect on every horizon: the header and the list stay one, so the
        // segmented control's thumb slides between them instead of being rebuilt.
        onRefresh = onRefresh,
        refreshing = state.refreshing,
    ) {
        if (statsRange == null) {
            todayBody(
                state = state,
                first = first,
                entrances = entrances,
                onOpenRecipe = onOpenRecipe,
                onInstallOrUpdate = { uriHandler.openUri(HEALTH_CONNECT_PLAY_URL) },
                onGrantPermissions = onGrantPermissions,
                onAddWater = onAddWater,
                onUndoWater = onUndoWater,
                onAddWorkout = { showAddWorkout = true },
                onDeleteWorkout = onDeleteWorkout,
            )
        } else {
            // One item for the whole statistics body: it switches between its ranges in place, so
            // the average hands its digits off (Week → Month) instead of arriving again.
            item(key = "stats", contentType = "stats") { statsBody(statsRange) }
        }
    }

    if (showAddWorkout) {
        AddWorkoutDialog(
            weightKg = state.weightKg,
            onConfirm = { type, amount ->
                onAddWorkout(type, amount)
                showAddWorkout = false
            },
            onDismiss = { showAddWorkout = false },
        )
    }
}

/** "Today · 855 left" / "Today · 290 over" while the title is scrolled away; "Today · Week" on statistics. */
@Composable
private fun compactTitle(title: String, state: DashboardUiState, range: TodayRange): String {
    val nf = rememberIntegerFormat()
    return when {
        range != TodayRange.TODAY -> "$title · ${stringResource(range.labelRes)}"
        state.loading || state.targets == null -> title
        state.remainingKcal < 0 -> stringResource(R.string.compact_over, title, nf.format(-state.remainingKcal.toLong()))
        else -> stringResource(R.string.compact_left, title, nf.format(state.remainingKcal.toLong()))
    }
}

/**
 * Back from Add (A5): Food search logged to a meal and popped back here; say so once ("Added to
 * Lunch") with the drawn check. The hand-off is consumed at once, so a later visit never repeats it.
 */
@Composable
private fun AddedToast(snackbarHostState: SnackbarHostState) {
    val handOff = LocalAddedHandOff.current
    val slot = handOff?.slot
    val scope = rememberCoroutineScope()
    val message = slot?.let { stringResource(R.string.toast_added_to, mealSlotLabel(it)) }
    LaunchedEffect(slot) {
        if (slot == null || message == null) return@LaunchedEffect
        // Launched outside this effect: consuming the hand-off restarts the effect, the toast stays.
        scope.launch { snackbarHostState.showSnackbar(EmberToastVisuals(message, ToastIcon.Check)) }
        handOff.consume()
    }
}

/**
 * How wide the streak chip may be: the content width less the large title, the 48 dp gear and the
 * gaps between them, so the chip never squeezes the title into breaking ("Сьогодн / і").
 */
// The width comes from the same source as the scaffold's own gutters (rememberGutter reads the
// configuration too), so this arithmetic matches the row the scaffold lays out.
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun streakRoom(title: String): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val content = min(LocalConfiguration.current.screenWidthDp.dp - rememberGutter() * 2, EmberSpace.ContentMaxWidth)
    val titleWidth = with(density) { measurer.measure(title, Ember.type.largeTitle).size.width.toDp() }
    // 12 dp between the title and the actions, 10 between the chip and the gear.
    return content - titleWidth - 12.dp - 10.dp - EmberSpace.TouchTarget
}

/**
 * "12-day streak" with the Ember flame, in a 30 dp surface capsule next to the gear. Where the full
 * words do not fit beside the title ([room]: long Ukrainian words, large text), it shows the flame
 * and the number alone, and TalkBack still reads the whole phrase.
 */
@Composable
private fun StreakChip(days: Int, room: Dp) {
    val c = Ember.colors
    val t = Ember.type
    val density = LocalDensity.current
    val text = pluralStringResource(R.plurals.streak_days, days, days)
    val style = t.footnote.copy(
        // Beside a 34 sp title the chip may grow only so much, or it would push the title apart.
        fontSize = t.footnote.fontSize.cappedAt(17.dp),
        fontWeight = FontWeight.SemiBold,
        color = c.label,
    )
    val measurer = rememberTextMeasurer()
    // Flame 15, gap 5, padding 9 + 12, the hairline.
    val full = with(density) { measurer.measure(text, style).size.width.toDp() } + 15.dp + 5.dp + 21.dp + 1.dp
    val compact = full > room
    Row(
        Modifier
            .heightIn(min = 30.dp)
            .emberShadow(Elevation.Card, EmberShapes.capsule, c.isDark)
            .background(c.surface, EmberShapes.capsule)
            .border(.5.dp, c.sep, EmberShapes.capsule)
            .padding(start = 9.dp, end = if (compact) 11.dp else 12.dp, top = 4.dp, bottom = 4.dp)
            .clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    ) {
        EmberIcon(EmberIcons.Flame, null, size = 15.dp, brush = EmberBrushes.emberIcon(c), strokeWidth = 2.4f)
        BasicText(
            if (compact) rememberIntegerFormat().format(days.toLong()) else text,
            style = if (compact) style.copy(fontFeatureSettings = "tnum") else style,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The Today body as list items, in the order of the day: Health Connect notices, the hero, today's
 * menu, macros and limits, activity, workouts, water, weight. On the first open of the day the
 * cards rise in a 60 ms cascade; later visits get the calm revisit entrance.
 */
private fun LazyListScope.todayBody(
    state: DashboardUiState,
    first: Boolean,
    entrances: EntranceState,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onInstallOrUpdate: () -> Unit,
    onGrantPermissions: () -> Unit,
    onAddWater: (ml: Int) -> Unit,
    onUndoWater: () -> Unit,
    onAddWorkout: () -> Unit,
    onDeleteWorkout: (id: Long) -> Unit,
) {
    if (state.loading) {
        item(key = "today-loading", contentType = "today-loading") { TodaySkeleton() }
        return
    }
    item(key = "hero", contentType = "hero") {
        Column {
            HealthNotices(state, onInstallOrUpdate, onGrantPermissions)
            // 22 dp from the hero to the first card: the list's 12 plus 10.
            HeroRing(state, Modifier.padding(bottom = 10.dp), first = first, entrances = entrances)
        }
    }
    fun Modifier.rise(index: Int, key: String) = emberEntrance(index, EntranceKind.Rise, first, entrances, key)
    if (state.todayMenu.isNotEmpty()) {
        item(key = "menu", contentType = "menu") {
            TodayMenuCard(state.todayMenu, onOpenRecipe, Modifier.rise(0, "menu"))
        }
    }
    item(key = "macros", contentType = "macros") { MacroCard(state, Modifier.rise(1, "macros"), first) }
    item(key = "tiles", contentType = "tiles") { ActivityTiles(state, Modifier.rise(2, "tiles")) }
    item(key = "workouts", contentType = "workouts") {
        WorkoutsCard(state, onAddWorkout, onDeleteWorkout, Modifier.rise(3, "workouts"))
    }
    item(key = "water", contentType = "water") {
        WaterCard(state.waterMl, onAddWater, onUndoWater, Modifier.rise(4, "water"), first)
    }
    item(key = "weight", contentType = "weight") { WeightCard(state, Modifier.rise(5, "weight"), first) }
}

/**
 * The Health Connect banner and the "no data" hint, each sliding in and out. The banner's text is
 * frozen during its exit: while it shrinks away the state is already NONE, which would otherwise
 * flash the wrong text for a moment.
 */
@Composable
private fun HealthNotices(state: DashboardUiState, onInstallOrUpdate: () -> Unit, onGrant: () -> Unit) {
    var shownBanner by remember { mutableStateOf(state.hcBanner) }
    if (state.hcBanner != HcBannerState.NONE) shownBanner = state.hcBanner
    AnimatedVisibility(
        visible = state.hcBanner != HcBannerState.NONE,
        enter = fadeIn(tween(360)) + expandVertically(tween(360)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(240)),
    ) {
        HcBannerCard(shownBanner, onInstallOrUpdate, onGrant, Modifier.padding(bottom = 14.dp))
    }
    AnimatedVisibility(
        visible = state.showNoDataHint,
        enter = fadeIn(tween(360)) + expandVertically(tween(360)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(240)),
    ) {
        NoDataHint(Modifier.padding(bottom = 14.dp))
    }
}

/**
 * Loading: the hero's ring track and two lines where the numbers will be, then three card shapes,
 * all at their final geometry and pulsing gently. TalkBack hears "Loading…" once.
 */
@Composable
private fun TodaySkeleton() {
    val ring = rememberHeroRingSize()
    val loading = stringResource(R.string.loading)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonRing(ring.size, ring.stroke, Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(18.dp))
        SkeletonLine(220.dp, height = 16.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(160.dp, height = 12.dp)
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.fillMaxWidth().height(196.dp))
            Skeleton(Modifier.fillMaxWidth().height(236.dp))
            Skeleton(Modifier.fillMaxWidth().height(120.dp))
        }
    }
}
