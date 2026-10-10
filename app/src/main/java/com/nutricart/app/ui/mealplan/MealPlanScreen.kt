package com.nutricart.app.ui.mealplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.domain.logic.MealPlanGenerator
import com.nutricart.app.domain.logic.PlanFailureReason
import com.nutricart.app.ui.common.CookedPortionsDialog
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRing
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.ToolButton
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.ember.rememberDecimalFormat
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberIntegerFormat
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** The weekly plan: 7 day cards with meals, lock/swap/log actions. */
@Composable
fun MealPlanScreen(
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Roll the week forward if the app slept past midnight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose { }
    }

    // Generation failures arrive as one-shot errors -> a warning toast.
    val noRecipesMessage = stringResource(R.string.plan_error_no_recipes)
    val unreachableMessage = stringResource(R.string.plan_error_unreachable)
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            EmberToastVisuals(
                when (error) {
                    PlanFailureReason.NO_RECIPES_FOR_SLOT -> noRecipesMessage
                    PlanFailureReason.TARGET_UNREACHABLE -> unreachableMessage
                },
                ToastIcon.Warning,
            )
        )
        viewModel.clearError()
    }

    // "Added to diary" confirmation fires only after the insert succeeded.
    val loggedMessage = stringResource(R.string.logged_to_diary)
    LaunchedEffect(state.loggedToDiary) {
        if (state.loggedToDiary) {
            snackbarHostState.showSnackbar(EmberToastVisuals(loggedMessage, ToastIcon.Check))
            viewModel.clearLoggedToDiary()
        }
    }

    MealPlanContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenRecipe = onOpenRecipe,
        onGenerate = viewModel::generate,
        onToggleLock = viewModel::toggleLock,
        onSwap = viewModel::swap,
        onAddToDiary = viewModel::addToDiary,
        onCook = viewModel::cook,
    )
}

/**
 * The stateless half of [MealPlanScreen]; it owns only the "cooked" sheet.
 *
 * An Ember page: the week as the eyebrow, the Regenerate button with its promise under it, then one
 * card per day with a small ring of the day's planned total. Regenerating deals a new week: every
 * meal whose dish changed hands its name over (old up and out, new up from below, staggered down the
 * week), kcal digits hand off, day rings sweep from their old totals, locked meals stay still and
 * glint once, and "On target" pops in where a day lands in the band.
 */
@Composable
fun MealPlanContent(
    state: MealPlanUiState,
    snackbarHostState: SnackbarHostState,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onGenerate: () -> Unit,
    onToggleLock: (PlanMealUi) -> Unit,
    onSwap: (PlanMealUi) -> Unit,
    onAddToDiary: (PlanMealUi) -> Unit,
    onCook: (meal: PlanMealUi, portions: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Which meal is being confirmed as cooked, by id; null = the sheet is closed. Saved, so the
    // sheet is still there after a rotation (the meal itself comes back with the state).
    var cookingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val cookingMeal = cookingId?.let { id -> state.days.firstNotNullOfOrNull { d -> d.meals.firstOrNull { it.id == id } } }
    // A regenerate can replace the meal under an open sheet: then there is nothing left to confirm.
    LaunchedEffect(state.loading, cookingMeal == null) {
        if (!state.loading && cookingMeal == null) cookingId = null
    }
    val cookedMessage = stringResource(R.string.fridge_cooked_toast)

    cookingMeal?.let { meal ->
        CookedPortionsDialog(
            onConfirm = { portions ->
                onCook(meal, portions)
                cookingId = null
                scope.launch { snackbarHostState.showSnackbar(EmberToastVisuals(cookedMessage, ToastIcon.Check)) }
            },
            onDismiss = { cookingId = null },
        )
    }

    val first = rememberFirstOpen("plan")
    val entrances = rememberEntranceState()
    val start = state.days.firstOrNull()?.epochDay ?: LocalDate.now().toEpochDay()
    val week = rememberWeekLabel(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(start + 6))
    // Read on every composition: after midnight the next resume rolls the plan to the new day.
    val today = LocalDate.now().toEpochDay()

    LargeTitleScaffold(
        title = stringResource(R.string.plan_title),
        eyebrow = week,
        toastHostState = snackbarHostState,
    ) {
        when {
            state.loading -> item(key = "loading", contentType = "loading") { PlanSkeleton() }

            !state.hasPlan -> item(key = "empty", contentType = "empty") {
                EmptyPlan(
                    generating = state.generating,
                    onGenerate = onGenerate,
                    modifier = Modifier.emberEntrance(0, EntranceKind.Rise, first, entrances, "empty"),
                )
            }

            else -> {
                item(key = "head", contentType = "head") {
                    PlanHead(
                        generating = state.generating,
                        onGenerate = onGenerate,
                        modifier = Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "head"),
                    )
                }
                // Meals are counted down the whole week, so a new week is dealt from the top.
                var dealt = 0
                state.days.filter { it.meals.isNotEmpty() }.forEachIndexed { index, day ->
                    val firstMeal = dealt
                    dealt += day.meals.size
                    item(key = day.epochDay, contentType = "day") {
                        DayCard(
                            day = day,
                            targetKcal = state.targetKcal,
                            today = today,
                            // While a generation runs the plan is being rewritten — freeze the
                            // per-meal actions to avoid racing it.
                            actionsEnabled = !state.generating,
                            // The diary cannot browse into the future, so logging a future meal
                            // would create an entry the user can't see or delete until that day —
                            // offer "+" only for today and the past.
                            canLogToDiary = day.epochDay <= today,
                            generating = state.generating,
                            sweep = first,
                            sweepDelay = 420 + 60 * index,
                            firstMeal = firstMeal,
                            onOpenRecipe = onOpenRecipe,
                            onToggleLock = onToggleLock,
                            onSwap = onSwap,
                            onAddToDiary = onAddToDiary,
                            onCooked = { cookingId = it.id },
                            modifier = Modifier.emberEntrance(index + 1, EntranceKind.Rise, first, entrances, "day-${day.epochDay}"),
                        )
                    }
                }
            }
        }
    }
}

/** Regenerate (the Ember spinner while it runs) and the promise the generator keeps. */
@Composable
private fun PlanHead(generating: Boolean, onGenerate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EmberButton(
            text = stringResource(R.string.regenerate_plan),
            onClick = onGenerate,
            variant = ButtonVariant.Ink,
            size = ButtonSize.Lg,
            icon = EmberIcons.Refresh,
            loading = generating,
        )
        ControlText(
            stringResource(R.string.plan_footnote),
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            style = Ember.type.footnote.copy(fontSize = 14.sp, lineHeight = 1.3.em),
            color = Ember.colors.label2,
            textAlign = TextAlign.Center,
        )
    }
}

/** No plan yet: the empty ring with the plan glyph, the hint, and Generate. */
@Composable
private fun EmptyPlan(generating: Boolean, onGenerate: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val (lead, rest) = splitHint(stringResource(R.string.plan_empty_hint), currentLocale())
    EmberCard(modifier, padding = PaddingValues(horizontal = 6.dp)) {
        EmptyState(
            title = lead,
            body = rest,
            art = {
                Ring(value = 0f, target = 1f, size = 96.dp, stroke = 12.dp) {
                    EmberIcon(EmberIcons.Plan, null, Modifier.align(Alignment.Center), size = 36.dp, brush = EmberBrushes.emberIcon(c))
                }
            },
            action = {
                EmberButton(
                    text = stringResource(R.string.generate_plan),
                    onClick = onGenerate,
                    variant = ButtonVariant.Ink,
                    size = ButtonSize.Lg,
                    icon = EmberIcons.Sparkle,
                    loading = generating,
                )
            },
        )
    }
}

/** True when the day is inside the band the generator promises (±5% of the target). */
private fun inBand(totalKcal: Int, targetKcal: Int): Boolean =
    targetKcal > 0 && abs(totalKcal - targetKcal).toDouble() / targetKcal <= MealPlanGenerator.KCAL_TOLERANCE

@Composable
private fun DayCard(
    day: PlanDayUi,
    targetKcal: Int,
    today: Long,
    actionsEnabled: Boolean,
    canLogToDiary: Boolean,
    generating: Boolean,
    sweep: Boolean,
    sweepDelay: Int,
    firstMeal: Int,
    onOpenRecipe: (Long, Double) -> Unit,
    onToggleLock: (PlanMealUi) -> Unit,
    onSwap: (PlanMealUi) -> Unit,
    onAddToDiary: (PlanMealUi) -> Unit,
    onCooked: (PlanMealUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    // animateContentSize: swapping a meal for one with a longer name resizes the card smoothly.
    EmberCard(modifier, padding = PaddingValues(0.dp)) {
        Column(Modifier.animateContentSize(motionSpec(EmberSprings.snappy()))) {
            DayHeader(day, targetKcal, today, sweep, sweepDelay)
            Spacer(Modifier.fillMaxWidth().height(.5.dp).background(c.sep))
            day.meals.forEachIndexed { i, meal ->
                // Keyed by the place in the day, not the row id: a regenerate writes new rows, and the
                // row must stay to hand its dish over.
                key(meal.slot, meal.position) {
                    if (i > 0) {
                        Row {
                            Spacer(Modifier.width(62.dp))
                            Spacer(Modifier.weight(1f).height(.5.dp).background(c.sep))
                        }
                    }
                    MealRow(
                        meal = meal,
                        actionsEnabled = actionsEnabled,
                        canLogToDiary = canLogToDiary,
                        generating = generating,
                        stagger = firstMeal + i,
                        onClick = { onOpenRecipe(meal.recipeId, meal.portionFactor) },
                        onToggleLock = { onToggleLock(meal) },
                        onSwap = { onSwap(meal) },
                        onAddToDiary = { onAddToDiary(meal) },
                        onCooked = { onCooked(meal) },
                    )
                }
            }
        }
    }
}

/**
 * "Today / Sat, Oct 10 · 2,220 / 2,240 kcal" with the day's ring and, inside the band, "On target"
 * (a word, not a colour). One heading for TalkBack: "Today, Sat, Oct 10 · 2,220 / 2,240 kcal, On target".
 */
@Composable
private fun DayHeader(day: PlanDayUi, targetKcal: Int, today: Long, sweep: Boolean, sweepDelay: Int) {
    val c = Ember.colors
    val t = Ember.type
    val locale = currentLocale()
    val date = LocalDate.ofEpochDay(day.epochDay)
    val title = when (day.epochDay) {
        today -> stringResource(R.string.tab_today)
        today + 1 -> stringResource(R.string.day_tomorrow)
        else -> longDate(date, locale)
    }
    val nf = rememberIntegerFormat()
    // The target and its unit never break apart ("2,240 kcal"); the line may break at the slash.
    val kcal = stringResource(R.string.plan_kcal_vs_target, nf.format(day.totalKcal.toLong()), nf.format(targetKcal.toLong()))
        .let { text -> text.lastIndexOf(' ').let { i -> if (i > 0) text.replaceRange(i, i + 1, "\u00A0") else text } }
    val sub = if (day.epochDay <= today + 1) "${shortDate(date, locale)} · $kcal" else kcal
    val onTarget = inBand(day.totalKcal, targetKcal)
    // With large text the pill goes under the totals, so they keep the width they need.
    val pillBelow = LocalDensity.current.fontScale >= 1.5f
    val pill: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = onTarget,
            enter = fadeIn(motionSpec(tween(160))) + scaleIn(motionSpec(EmberSprings.bouncy()), initialScale = .4f),
            exit = fadeOut(motionSpec(tween(120, easing = EmberEasing.In))) + scaleOut(motionSpec(tween(120)), targetScale = .8f),
        ) { OnTargetPill() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ring(
            value = day.totalKcal.toFloat(),
            target = targetKcal.toFloat(),
            size = RingSizes.DayCard.size,
            stroke = RingSizes.DayCard.stroke,
            sweep = sweep,
            delayMillis = sweepDelay,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ControlText(title, style = t.headline.copy(lineHeight = 1.25.em), color = c.label)
            ControlText(sub, style = t.footnote, color = c.label2)
            if (pillBelow) Box(Modifier.padding(top = 4.dp)) { pill() }
        }
        if (!pillBelow) pill()
    }
}

@Composable
private fun OnTargetPill() {
    val c = Ember.colors
    Row(
        Modifier
            .background(c.goodSoft, EmberShapes.capsule)
            .padding(start = 5.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EmberIcon(EmberIcons.Check, null, size = 13.dp, tint = c.good, strokeWidth = 3f)
        ControlText(
            stringResource(R.string.plan_on_target),
            style = Ember.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.good,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One planned meal: its tile, "Lunch · ×1.20", the dish (two lines at most) and its kcal, then the
 * tools. The row opens the recipe and reads as one TalkBack stop (with the tools also as custom
 * actions); each tool stays its own button.
 */
@Composable
private fun MealRow(
    meal: PlanMealUi,
    actionsEnabled: Boolean,
    canLogToDiary: Boolean,
    generating: Boolean,
    stagger: Int,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onSwap: () -> Unit,
    onAddToDiary: () -> Unit,
    onCooked: () -> Unit,
) {
    val c = Ember.colors
    val t = Ember.type
    val reduced = Ember.motion.reduced
    // What the row shows of its dish. A regenerate deals the new week from the top: each row keeps
    // its old dish for 60 ms per meal above it, then hands name, kcal and portion over together.
    val dealt = rememberDealt(
        Dish(meal.recipeName, meal.kcal, meal.portionFactor),
        delayMillis = if (generating) 60 * stagger.coerceAtMost(MaxStagger) else 0,
    )
    val factor = stringResource(R.string.plan_portion_factor, rememberDecimalFormat(2).format(dealt.portionFactor))
    val lockLabel = stringResource(if (meal.isLocked) R.string.unlock_meal else R.string.lock_meal)
    val swapLabel = stringResource(R.string.swap_meal)
    val cookedLabel = stringResource(R.string.fridge_cooked_action)
    val logLabel = stringResource(R.string.log_meal)

    // "+" turns into a check for a moment after a tap (the toast confirms the write itself).
    var justLogged by remember { mutableStateOf(false) }
    LaunchedEffect(justLogged) {
        if (justLogged) {
            motionDelay(1500)
            justLogged = false
        }
    }
    val logIt = {
        onAddToDiary()
        justLogged = true
    }

    // A locked meal stays put through a regenerate; when the new week has landed its lock glints once.
    val glint = remember { Animatable(1f) }
    var wasGenerating by remember { mutableStateOf(generating) }
    LaunchedEffect(generating) {
        if (wasGenerating && !generating && meal.isLocked && !reduced) {
            motionDelay(60L * stagger.coerceAtMost(MaxStagger))
            glint.snapTo(.9f)
            glint.animateTo(1f, EmberSprings.bouncy())
        }
        wasGenerating = generating
    }

    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                onClick = onClick,
            )
            .semantics {
                if (actionsEnabled) {
                    customActions = buildList {
                        add(CustomAccessibilityAction(lockLabel) { onToggleLock(); true })
                        if (!meal.isLocked) add(CustomAccessibilityAction(swapLabel) { onSwap(); true })
                        add(CustomAccessibilityAction(cookedLabel) { onCooked(); true })
                        if (canLogToDiary) add(CustomAccessibilityAction(logLabel) { logIt(); true })
                    }
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealTile(meal.slot)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlText(
                            "${mealSlotLabel(meal.slot)} · $factor",
                            style = t.footnote.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em),
                            color = c.label2,
                        )
                        if (meal.isLocked) LockedBadge()
                    }
                    DishName(dealt.name)
                }
                NumberWithUnit(
                    value = dealt.kcal.toLong(),
                    unit = stringResource(R.string.kcal_unit),
                    numberStyle = t.rowNumber,
                    modifier = Modifier.padding(top = 2.dp),
                    unitSize = 12.sp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .bleedStart(6.dp)
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(
                    icon = if (meal.isLocked) EmberIcons.Lock else EmberIcons.LockOpen,
                    contentDescription = lockLabel,
                    onClick = onToggleLock,
                    selected = meal.isLocked,
                    enabled = actionsEnabled,
                    modifier = Modifier.graphicsLayer {
                        scaleX = glint.value
                        scaleY = glint.value
                    },
                )
                ToolButton(EmberIcons.Shuffle, swapLabel, onSwap, enabled = actionsEnabled && !meal.isLocked)
                // "I cooked this" belongs where the meal already is: cooking starts on the plan far
                // more often than on the recipe screen.
                ToolButton(EmberIcons.Pot, cookedLabel, onCooked, enabled = actionsEnabled)
                if (canLogToDiary) {
                    ToolButton(
                        icon = if (justLogged) EmberIcons.Check else EmberIcons.Plus,
                        contentDescription = logLabel,
                        onClick = logIt,
                        selected = justLogged,
                        enabled = actionsEnabled,
                    )
                }
                Spacer(Modifier.weight(1f))
                EmberIcon(EmberIcons.Right, null, size = 20.dp, tint = c.label3)
            }
        }
    }
}

/** A regenerate deals the meals out one after another, but never for longer than about a second. */
private const val MaxStagger = 16

/** "Locked" with a small lock after the slot line of a locked meal (12 sp, label 2). */
@Composable
private fun LockedBadge() {
    val c = Ember.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        EmberIcon(EmberIcons.Lock, null, size = 12.dp, tint = c.label2, strokeWidth = 2.6f)
        ControlText(
            stringResource(R.string.plan_locked),
            style = Ember.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.label2,
            maxLines = 1,
        )
    }
}

/** The point of a dish name's hand-off where the old name is gone and the new one starts to rise. */
private const val HandOffAt = .3f

/** What a meal row shows of its dish (it may lag the plan for a moment during a regenerate). */
private data class Dish(val name: String, val kcal: Int, val portionFactor: Double)

/**
 * [value], but a change reaches the screen [delayMillis] later (scaled by the animator setting).
 * The first value shows at once.
 */
@Composable
private fun <T> rememberDealt(value: T, delayMillis: Int): T {
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != shown) {
            if (delayMillis > 0) motionDelay(delayMillis.toLong())
            shown = value
        }
    }
    return shown
}

/**
 * The dish's name, two lines at most. When the dish changes (a swap, a regenerate) the old name
 * slides up and fades out, and only then does the new one rise from below, the way a digit hands
 * off: two names never share the line, so the row never shows stacked text. Each name keeps its own
 * node for as long as it shows (keyed by the text), with its layer from the start, so the old one
 * is drawn unchanged until it starts to leave.
 */
@Composable
private fun DishName(name: String) {
    val c = Ember.colors
    val style = Ember.type.callout.copy(fontWeight = FontWeight.Medium, lineHeight = 1.3.em)
    val reduced = Ember.motion.reduced
    var current by remember { mutableStateOf(name) }
    var leaving by remember { mutableStateOf<String?>(null) }
    // 0 = the hand-off starts … 1 = the new name is in place (420 ms, the curves below shape it).
    val p = remember { Animatable(1f) }
    LaunchedEffect(name) {
        if (name == current) return@LaunchedEffect
        if (reduced) {
            current = name
            leaving = null
            return@LaunchedEffect
        }
        leaving = current
        current = name
        p.snapTo(0f)
        p.animateTo(1f, tween(420, easing = LinearEasing))
        leaving = null
    }
    Box(Modifier.clipToBounds()) {
        for (text in listOfNotNull(leaving, current)) {
            key(text) {
                ControlText(
                    text,
                    Modifier.graphicsLayer {
                        // What this name is doing is decided here, from the state as it is when the
                        // layer draws, never from the last composition: a hand-off can start, or end,
                        // in a frame that draws before the names recompose.
                        if (reduced) return@graphicsLayer
                        val t = p.value
                        when (text) {
                            current -> if (leaving != null) {
                                // Invisible until the old name is gone, then up from half a line below.
                                val e = ((t - HandOffAt) / (1f - HandOffAt)).coerceIn(0f, 1f)
                                alpha = EmberEasing.Out.transform((e / .6f).coerceIn(0f, 1f))
                                translationY = size.height * .5f * (1f - EmberEasing.Snappy.transform(e))
                            }
                            leaving -> {
                                // Gone by 30% (126 ms), sliding up half its height on the way.
                                val e = EmberEasing.In.transform((t / HandOffAt).coerceIn(0f, 1f))
                                alpha = 1f - e
                                translationY = -size.height * .5f * e
                            }
                            // A name already handed off whose node has not gone yet.
                            else -> alpha = 0f
                        }
                    },
                    style = style,
                    color = c.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Two day cards at their final geometry while the plan loads (decorative; it says "Loading" once). */
@Composable
private fun PlanSkeleton() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(54.dp), EmberShapes.capsule)
        SkeletonLine(240.dp, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), height = 12.dp)
        repeat(2) {
            EmberCard(padding = PaddingValues(0.dp)) {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonRing(RingSizes.DayCard.size, RingSizes.DayCard.stroke)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(90.dp, height = 16.dp)
                        SkeletonLine(170.dp, height = 12.dp)
                    }
                }
                repeat(3) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Skeleton(Modifier.size(34.dp), EmberShapes.tileIcon)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonLine(110.dp, height = 11.dp)
                            SkeletonLine(210.dp, height = 14.dp)
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(4) { Skeleton(Modifier.size(36.dp), EmberShapes.circle) }
                            }
                        }
                        Box(Modifier.padding(top = 2.dp)) { SkeletonLine(56.dp, height = 14.dp) }
                    }
                }
            }
        }
    }
}
