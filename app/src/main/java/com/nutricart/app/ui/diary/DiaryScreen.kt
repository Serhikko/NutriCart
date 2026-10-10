package com.nutricart.app.ui.diary

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.domain.logic.PartnerDigest
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.partner.partnerLabels
import com.nutricart.app.ui.common.DayBudget
import com.nutricart.app.ui.common.DayBudgetViewModel
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.DayNav
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmberTextField
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberSharedBounds
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.ember.pressScale
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.ember.rememberLastShown
import com.nutricart.app.ui.ember.rememberLastShownValue
import com.nutricart.app.ui.navigation.LocalAddedHandOff
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** The food diary: one day at a time, four meal cards, add/delete entries. */
@Composable
fun DiaryScreen(
    onAddFood: (epochDay: Long, slot: MealSlot) -> Unit,
    viewModel: DiaryViewModel = hiltViewModel(),
    budgetViewModel: DayBudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val savingSlot by viewModel.savingSlot.collectAsState()
    val mealSaved by viewModel.mealSaved.collectAsState()
    val editingNote by viewModel.editingNote.collectAsState()
    val budget by budgetViewModel.budget.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep the "today" limit of the day selector correct after midnight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    // The budget follows the displayed day ("898 left of 2,240").
    LaunchedEffect(state.epochDay) { budgetViewModel.show(state.epochDay) }

    // One-shot confirmation after saving a meal.
    val savedMessage = stringResource(R.string.saved_meal_saved)
    LaunchedEffect(mealSaved) {
        if (mealSaved) {
            snackbarHostState.showSnackbar(EmberToastVisuals(savedMessage, ToastIcon.Check))
            viewModel.clearMealSaved()
        }
    }

    DiaryContent(
        state = state,
        savingSlot = savingSlot,
        editingNote = editingNote,
        snackbarHostState = snackbarHostState,
        onAddFood = onAddFood,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onEditNote = viewModel::startEditingNote,
        onDelete = viewModel::delete,
        onSaveAsMeal = viewModel::startSavingMeal,
        onSaveMeal = viewModel::saveMeal,
        onCancelSavingMeal = viewModel::cancelSavingMeal,
        onSaveNote = viewModel::saveNote,
        onCancelEditingNote = viewModel::cancelEditingNote,
        budget = budget,
    )
}

/**
 * The stateless half of [DiaryScreen]. [savingSlot] non-null shows the "name this meal" sheet;
 * [editingNote] shows the day-note sheet. [budget] (the day's target, when known) adds the summary
 * ring and "898 left of 2,240"; without it the summary shows the total alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryContent(
    state: DiaryUiState,
    savingSlot: MealSlot?,
    editingNote: Boolean,
    snackbarHostState: SnackbarHostState,
    onAddFood: (epochDay: Long, slot: MealSlot) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: (FoodLogEntryEntity) -> Unit,
    onSaveAsMeal: (MealSlot) -> Unit,
    onSaveMeal: (name: String) -> Unit,
    onCancelSavingMeal: () -> Unit,
    onSaveNote: (text: String) -> Unit,
    onCancelEditingNote: () -> Unit,
    budget: DayBudget? = null,
) {
    val format = rememberIntegerFormat()
    val title = stringResource(R.string.diary_title)
    val kcalUnit = stringResource(R.string.kcal_unit)
    val total = state.totals.kcal.roundToInt()
    val first = rememberFirstOpen("diary")
    val entrances = rememberEntranceState()
    val memory = remember { DayMemory() }
    val reduced = Ember.motion.reduced
    val shift = with(LocalDensity.current) { 24.dp.roundToPx() }

    // Back from Food search after a log: "Added to Lunch", once. The toast runs in the screen's own
    // scope, because handing the slot back (consume) recomposes this effect away at once.
    val handOff = LocalAddedHandOff.current
    val addedSlot = handOff?.slot
    val addedMessage = addedSlot?.let { stringResource(R.string.toast_added_to, mealSlotLabel(it)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(addedSlot) {
        if (handOff != null && addedMessage != null) {
            scope.launch { snackbarHostState.showSnackbar(EmberToastVisuals(addedMessage, ToastIcon.Check)) }
            handOff.consume()
        }
    }

    LargeTitleScaffold(
        title = title,
        eyebrow = rememberLongDate(LocalDate.ofEpochDay(state.epochDay)),
        compactTitle = "$title · ${format.format(total)} $kcalUnit",
        actions = { ShareDayAction(state) },
        below = {
            DayNav(
                label = dayNavLabel(state),
                onPrevious = onPreviousDay,
                onNext = onNextDay,
                nextEnabled = !state.isToday,
                previousLabel = stringResource(R.string.previous_day),
                nextLabel = stringResource(R.string.next_day),
            )
        },
        toastHostState = snackbarHostState,
    ) {
        item(key = "day", contentType = "day") {
            // A day change slides the new day in from the side it comes from (T11); the content
            // key is the day, so the same day's updates (a delete, a note) never slide.
            AnimatedContent(
                targetState = state,
                contentKey = { it.epochDay },
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    val dir = if (targetState.epochDay >= initialState.epochDay) 1 else -1
                    if (reduced) {
                        (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false))
                    } else {
                        // The old day is nearly gone before the new one shows, so two days never
                        // read on top of each other.
                        (fadeIn(tween(360, 80, EmberEasing.Out)) +
                            slideInHorizontally(tween(360, 60, EmberEasing.Smooth)) { shift * dir })
                            .togetherWith(
                                fadeOut(tween(140, easing = EmberEasing.In)) +
                                    slideOutHorizontally(tween(140, easing = EmberEasing.In)) { -shift * dir },
                            )
                            .using(SizeTransform(clip = false))
                    }
                },
                label = "day",
            ) { day ->
                DayBody(
                    state = day,
                    targetKcal = budget?.targetKcal,
                    first = first,
                    entrances = entrances,
                    memory = memory,
                    onAddFood = onAddFood,
                    onEditNote = onEditNote,
                    onDelete = onDelete,
                    onSaveAsMeal = onSaveAsMeal,
                )
            }
        }
    }
    // After the frame: what this screen showed, so the next day can hand off from it.
    SideEffect { memory.update(state) }

    // "Name this meal" for the section being saved.
    savingSlot?.let {
        SaveMealSheet(onConfirm = onSaveMeal, onDismiss = onCancelSavingMeal)
    }

    if (editingNote) {
        NoteSheet(
            initialText = state.note ?: "",
            onConfirm = onSaveNote,
            onDismiss = onCancelEditingNote,
        )
    }
}

/**
 * What the screen last showed (the day, its total and its meal totals). A new day reads it while it
 * first composes, before this frame's update, so its ring and numbers start from the previous day.
 */
private class DayMemory {
    var day: Long? = null
        private set
    var total: Int = 0
        private set
    val slots = HashMap<MealSlot, Int>()

    fun update(state: DiaryUiState) {
        day = state.epochDay
        total = state.totals.kcal.roundToInt()
        MealSlot.entries.forEach { slots[it] = slotKcal(state.entriesBySlot[it].orEmpty()) }
    }
}

/** How a day's numbers arrive: a day change hands off from the previous day's numbers. */
private class Arrival(val dayChange: Boolean, val fromTotal: Int?, val fromSlots: Map<MealSlot, Int>)

private fun slotKcal(entries: List<FoodLogEntryEntity>): Int = entries.sumOf { it.kcal }.roundToInt()

@Composable
private fun DayBody(
    state: DiaryUiState,
    targetKcal: Int?,
    first: Boolean,
    entrances: EntranceState,
    memory: DayMemory,
    onAddFood: (epochDay: Long, slot: MealSlot) -> Unit,
    onEditNote: () -> Unit,
    onDelete: (FoodLogEntryEntity) -> Unit,
    onSaveAsMeal: (MealSlot) -> Unit,
) {
    val day = state.epochDay
    val arrival = remember {
        val shown = memory.day
        if (shown != null && shown != day) Arrival(true, memory.total, HashMap(memory.slots)) else Arrival(false, null, emptyMap())
    }
    // The first build plays only on the first open of the day, and not again on a day change.
    val build = first && !arrival.dayChange
    val label = Ember.colors.label
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DaySummary(
            state = state,
            targetKcal = targetKcal,
            build = build,
            arrival = arrival,
            modifier = Modifier.emberEntrance(0, EntranceKind.Rise, first, entrances, "summary"),
        )
        NoteRow(
            note = state.note,
            onEdit = onEditNote,
            modifier = Modifier.emberEntrance(1, EntranceKind.Rise, first, entrances, "note"),
        )
        BasicText(
            stringResource(R.string.meals_section),
            style = Ember.type.title2,
            color = { label },
            modifier = Modifier
                // The section head's 30 dp above (18 + the stack's 12), nothing extra below.
                .padding(start = 2.dp, end = 2.dp, top = 18.dp)
                .semantics { heading() }
                .emberEntrance(2, EntranceKind.FadeUp, first, entrances, "meals"),
        )
        MealSlot.entries.forEachIndexed { i, slot ->
            MealCard(
                slot = slot,
                day = day,
                entries = state.entriesBySlot[slot].orEmpty(),
                build = build,
                arrival = arrival,
                onAdd = { onAddFood(day, slot) },
                onDelete = onDelete,
                onSaveAsMeal = { onSaveAsMeal(slot) },
                modifier = Modifier.emberEntrance(3 + i, EntranceKind.Rise, first, entrances, "meal/$slot"),
            )
        }
    }
}

/**
 * The day summary: the 52 dp ring (the shared element of "back from Add"), the day total and
 * "Total · 898 left of 2,240" (or "Total" alone without a budget). TalkBack reads the day line.
 */
@Composable
private fun DaySummary(
    state: DiaryUiState,
    targetKcal: Int?,
    build: Boolean,
    arrival: Arrival,
    modifier: Modifier,
) {
    val c = Ember.colors
    val format = rememberIntegerFormat()
    val day = state.epochDay
    val total = state.totals.kcal.roundToInt()
    val lastRing = rememberLastShown("ring/$day", total.toFloat())
    val lastTotal = rememberLastShownValue("diary-total/$day", total.toLong())
    // Back from Add: the ring and the total hand off from what they showed before Food search.
    val backFromAdd = !build && !arrival.dayChange && lastTotal != null && lastTotal != total.toLong()
    val fromTotal = when {
        arrival.dayChange -> arrival.fromTotal?.toLong()
        backFromAdd -> lastTotal
        else -> null
    }
    val totalDelay = when {
        build -> 420
        arrival.dayChange -> 120
        else -> 600
    }

    // "898 left of 2,240" follows the total the digits show, not the new one, so the two never
    // disagree while the total is still handing off (1,235 + 898 would not make 2,240).
    val shownTotal = rememberCaptionValue(total, fromTotal?.toInt(), totalDelay)
    val line = when {
        targetKcal == null -> stringResource(R.string.day_total)
        shownTotal <= targetKcal -> stringResource(R.string.diary_total_left, format.format(targetKcal - shownTotal), format.format(targetKcal))
        else -> stringResource(R.string.diary_total_over, format.format(shownTotal - targetKcal), format.format(targetKcal))
    }
    val described = targetKcal?.let {
        if (total <= it) {
            stringResource(R.string.ring_day_line, format.format(total), format.format(it), format.format(it - total))
        } else {
            stringResource(R.string.ring_day_line_over, format.format(total), format.format(it), format.format(total - it))
        }
    }
    EmberCard(
        modifier = modifier,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        mergeDescendants = true,
    ) {
        Row(
            Modifier.then(if (described != null) Modifier.clearAndSetSemantics { contentDescription = described } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (targetKcal != null) {
                val (from, sweep, delay) = when {
                    arrival.dayChange -> Triple(arrival.fromTotal?.toFloat(), true, 0)
                    build -> Triple(null, true, 420)
                    backFromAdd -> Triple(lastRing, true, 460)
                    else -> Triple(null, false, 0)
                }
                Ring(
                    value = total.toFloat(),
                    target = targetKcal.toFloat(),
                    size = RingSizes.Summary.size,
                    stroke = RingSizes.Summary.stroke,
                    modifier = Modifier.emberSharedBounds("day-ring/$day"),
                    from = from,
                    sweep = sweep,
                    delayMillis = delay,
                )
            }
            Column(Modifier.weight(1f)) {
                NumberWithUnit(
                    value = total.toLong(),
                    unit = stringResource(R.string.kcal_unit),
                    numberStyle = Ember.type.stat.copy(fontSize = 26.sp),
                    enter = build,
                    delayMillis = totalDelay,
                    from = fromTotal,
                )
                BasicText(
                    line,
                    style = Ember.type.footnote,
                    color = { c.label2 },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The day's note (Note glyph, "Note", three lines), or a quiet "+ Day note" link. Both open the sheet. */
@Composable
private fun NoteRow(note: String?, onEdit: () -> Unit, modifier: Modifier) {
    val c = Ember.colors
    if (note == null) {
        Box(modifier.fillMaxWidth()) {
            PlainLink(
                text = stringResource(R.string.day_note_add),
                onClick = onEdit,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    } else {
        EmberCard(modifier = modifier, onClick = onEdit, onClickLabel = stringResource(R.string.day_note_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmberIcon(EmberIcons.Note, null, Modifier.padding(top = 1.dp), size = 22.dp, tint = c.tint)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        stringResource(R.string.day_note_label),
                        style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = { c.label2 },
                    )
                    BasicText(
                        note,
                        style = Ember.type.subhead.copy(fontWeight = FontWeight.Normal, lineHeight = 1.4.em),
                        color = { c.label },
                        // Three lines at most: the meals stay in sight under a long note.
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One meal (the web's MealCard): the meal tile, its name (a heading), "2 items · from 08:05" or
 * "Nothing yet", the meal's kcal; the entries as inset rows with a delete button; then "+ Add food"
 * and, when the meal has entries, "Save as meal". Adding or removing an entry resizes the card.
 */
@Composable
private fun MealCard(
    slot: MealSlot,
    day: Long,
    entries: List<FoodLogEntryEntity>,
    build: Boolean,
    arrival: Arrival,
    onAdd: () -> Unit,
    onDelete: (FoodLogEntryEntity) -> Unit,
    onSaveAsMeal: () -> Unit,
    modifier: Modifier,
) {
    val c = Ember.colors
    val kcal = slotKcal(entries)
    val lastKcal = rememberLastShownValue("slot/$day/$slot", kcal.toLong())
    // Entries this meal showed before the screen last left (null the first time): anything not in it
    // was just added in Food search, and unfolds with the Ember glow (A4).
    val seen = rememberLastShownValue("entries/$day/$slot", entries.map { it.id }.toSet())
    val timeFormat = rememberTimeFormat()
    val backFromAdd = !build && !arrival.dayChange && lastKcal != null && lastKcal != kcal.toLong()

    EmberCard(modifier = modifier, padding = PaddingValues(0.dp)) {
        Column(Modifier.animateContentSize(EmberSprings.snappy())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MealTile(slot)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        mealSlotLabel(slot),
                        style = Ember.type.headline.copy(lineHeight = 1.25.em),
                        color = { c.label },
                        modifier = Modifier.semantics { heading() },
                    )
                    val sub = if (entries.isEmpty()) {
                        stringResource(R.string.meal_empty)
                    } else {
                        val from = entries.minOf { it.loggedAtEpochMillis }
                        pluralStringResource(R.plurals.meal_items, entries.size, entries.size, timeFormat.of(from))
                    }
                    BasicText(sub, style = Ember.type.footnote, color = { c.label2 })
                }
                NumberWithUnit(
                    value = kcal.toLong(),
                    unit = stringResource(R.string.kcal_unit),
                    numberStyle = Ember.type.kpi.copy(fontSize = 19.sp),
                    unitSize = 12.sp,
                    enter = build,
                    delayMillis = when {
                        build -> 520
                        arrival.dayChange -> 180
                        else -> 540
                    },
                    from = when {
                        arrival.dayChange -> arrival.fromSlots[slot]?.toLong()
                        backFromAdd -> lastKcal
                        else -> null
                    },
                )
            }
            if (entries.isNotEmpty()) {
                Column(Modifier.padding(start = 62.dp).hairline(c.sep)) {
                    entries.forEachIndexed { i, entry ->
                        // Keyed by the entry: a row's unfold and glow belong to that food, so a delete or
                        // an import while the Diary is open never hands them to a neighbour.
                        key(entry.id) {
                            val fresh = seen != null && entry.id !in seen
                            EntryRow(
                                entry = entry,
                                time = timeFormat.of(entry.loggedAtEpochMillis),
                                fresh = fresh,
                                divider = i > 0,
                                onDelete = { onDelete(entry) },
                            )
                        }
                    }
                }
            }
            AddRow(onAdd = onAdd, onSaveAsMeal = if (entries.isNotEmpty()) onSaveAsMeal else null)
        }
    }
}

/**
 * One logged food: its name (two lines at most), "120 g · 13:02" (no amount for plan snapshots),
 * the kcal and a delete button (immediate, as always). A [fresh] row unfolds and an Ember glow
 * fades off it.
 */
@Composable
private fun EntryRow(
    entry: FoodLogEntryEntity,
    time: String,
    fresh: Boolean,
    divider: Boolean,
    onDelete: () -> Unit,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val visible = remember { MutableTransitionState(!fresh || reduced).apply { targetState = true } }
    val glow = remember { Animatable(if (fresh && !reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (glow.value > 0f) glow.animateTo(0f, tween(1800, 900, EmberEasing.Out))
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = expandVertically(EmberSprings.snappy(), expandFrom = Alignment.Top) + fadeIn(tween(360, easing = EmberEasing.Out)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .then(if (divider) Modifier.hairline(c.sep) else Modifier)
                .drawBehind {
                    // The wash reaches back under the meal tile's column, as on the web.
                    val g = glow.value
                    if (g > 0f) {
                        val back = 62.dp.toPx()
                        drawRect(c.emberGlow.copy(alpha = c.emberGlow.alpha * g), Offset(-back, 0f), Size(size.width + back, size.height))
                    }
                }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Large text: the kcal goes under the name, so the name has the row's width and a word like
            // "Cappuccino" is never cut in two beside a fixed-width number.
            val stacked = LocalDensity.current.fontScale >= 1.5f
            Row(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val kcal: @Composable () -> Unit = {
                    NumberWithUnit(
                        value = entry.kcal.roundToInt().toLong(),
                        unit = stringResource(R.string.kcal_unit),
                        numberStyle = Ember.type.rowNumber,
                        unitSize = 12.sp,
                    )
                }
                Column(Modifier.weight(1f)) {
                    BasicText(
                        entry.name,
                        style = Ember.type.callout,
                        color = { c.label },
                        maxLines = if (stacked) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val amount = when {
                        // Logged in portions: show them, grams stay the math source.
                        entry.servings != null -> stringResource(R.string.portions_amount, entry.servings)
                        entry.grams != null -> stringResource(R.string.grams_value, entry.grams.roundToInt())
                        else -> null // a plan snapshot (no product)
                    }
                    BasicText(
                        if (amount != null) "$amount · $time" else time,
                        style = Ember.type.footnote,
                        color = { c.label2 },
                    )
                    if (stacked) Box(Modifier.padding(top = 2.dp)) { kcal() }
                }
                if (!stacked) kcal()
            }
            EmberIconButton(
                icon = EmberIcons.Trash,
                contentDescription = stringResource(R.string.delete_entry),
                onClick = onDelete,
                style = IconButtonStyle.Plain,
                size = 40.dp,
            )
        }
    }
}

/**
 * "+ Add food" (the whole left part is the button) and, for a meal with entries, "Save as meal" on
 * the right. When the two labels do not fit side by side in full (Ukrainian, large text, a narrow
 * phone), Save as meal takes its own row under Add food, its glyph and words lined up with it, so
 * neither label is ever cut.
 */
@Composable
private fun AddRow(onAdd: () -> Unit, onSaveAsMeal: (() -> Unit)?) {
    val c = Ember.colors
    val style = Ember.type.body.copy(fontWeight = FontWeight.Medium)
    val addLabel = stringResource(R.string.add_food)
    val saveLabel = stringResource(R.string.save_as_meal)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().hairline(c.sep)) {
        val room = constraints.maxWidth
        val oneLine = onSaveAsMeal == null || remember(addLabel, saveLabel, style, density, room) {
            fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1, density = density).size.width
            // Add food's 16 + 34 + 12 + 8 dp round its label, the link's 20 dp glyph, 4 dp gap and
            // 12 dp end, and 8 dp of air between the two.
            val chrome = with(density) { (16 + 34 + 12 + 8 + 20 + 4 + 12 + 8).dp.roundToPx() }
            width(addLabel) + width(saveLabel) + chrome <= room
        }
        if (oneLine) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                FooterAction(addLabel, EmberIcons.Plus, c.tint, onAdd, Modifier.weight(1f))
                if (onSaveAsMeal != null) {
                    // Freeze this whole meal as a one-tap combo for later.
                    PlainLink(
                        text = saveLabel,
                        onClick = onSaveAsMeal,
                        icon = EmberIcons.Bookmark,
                        color = c.label2,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                FooterAction(addLabel, EmberIcons.Plus, c.tint, onAdd, Modifier.fillMaxWidth())
                if (onSaveAsMeal != null) {
                    FooterAction(
                        saveLabel, EmberIcons.Bookmark, c.label2, onSaveAsMeal,
                        Modifier.fillMaxWidth().hairline(c.sep, start = 62.dp),
                    )
                }
            }
        }
    }
}

/**
 * One action of a meal card's footer as a full row, 52 dp min: a 34 dp glyph column (the "+" on its
 * tint disc, or the bare Save glyph) and the label, which wraps between words when it must.
 */
@Composable
private fun FooterAction(text: String, icon: EmberIcons, color: Color, onClick: () -> Unit, modifier: Modifier) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).pressScale(source, .9f), contentAlignment = Alignment.Center) {
            if (icon == EmberIcons.Plus) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(EmberShapes.circle)
                        .background(color.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EmberIcon(icon, null, size = 16.dp, tint = color)
                }
            } else {
                EmberIcon(icon, null, size = 20.dp, tint = color)
            }
        }
        BasicText(text, style = Ember.type.body.copy(fontWeight = FontWeight.Medium), color = { color })
    }
}

/** A 0.5 dp [color] line over the top edge, from [start] to the end (mirrored right to left). */
private fun Modifier.hairline(color: Color, start: Dp = 0.dp): Modifier = drawBehind {
    val w = .5.dp.toPx()
    val s = start.toPx()
    val left = if (layoutDirection == LayoutDirection.Rtl) 0f else s
    val right = if (layoutDirection == LayoutDirection.Rtl) size.width - s else size.width
    drawRect(color, Offset(left, 0f), Size(right - left, w))
}

/**
 * Sends the displayed day as plain text through any messenger — the zero-setup way to show
 * someone what you ate. Same formatter as the Telegram partner messages, so both read identically.
 */
@Composable
private fun ShareDayAction(state: DiaryUiState) {
    val context = LocalContext.current
    val hasEntries = state.entriesBySlot.values.any { it.isNotEmpty() }
    // The share title is resolved here, in composition, so it follows the current configuration.
    val date = LocalDate.ofEpochDay(state.epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val title = stringResource(R.string.partner_day_title, date)
    EmberIconButton(
        icon = EmberIcons.Share,
        contentDescription = stringResource(R.string.share_day_action),
        onClick = {
            val items = state.entriesBySlot.values.flatten()
                .sortedBy { it.loggedAtEpochMillis }
                .map { PartnerDigest.Item(it.meal.ordinal, it.name, it.kcal) }
            val text = PartnerDigest.dayMessage(
                title = title,
                items = items,
                eatenKcal = state.totals.kcal,
                targetKcal = null, // the share text stays as it always was
                labels = partnerLabels(context),
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
        style = IconButtonStyle.Surface,
        enabled = hasEntries,
    )
}

/** Free-text editor in a sheet; saving blank text deletes the note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val title = stringResource(R.string.day_note_title)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
        SheetHeader(title = title)
        // The label sits above the well (no floating label): the long hint reads in full.
        EmberTextField(
            value = text,
            // A note is a margin scribble, not an essay — cap the length.
            onValueChange = { text = it.take(500) },
            label = stringResource(R.string.day_note_hint),
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        SheetActions(
            confirmLabel = stringResource(R.string.save),
            onConfirm = { onConfirm(text) },
            onCancel = onDismiss,
        )
    }
}

/** Asks for a name and hands it back — the ViewModel does the saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveMealSheet(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val title = stringResource(R.string.saved_meal_name_title)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
        SheetHeader(title = title)
        EmberTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.saved_meal_name_label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
        )
        SheetActions(
            confirmLabel = stringResource(R.string.save),
            onConfirm = { onConfirm(name) },
            onCancel = onDismiss,
            confirmEnabled = name.isNotBlank(),
        )
    }
}

/**
 * A sheet's Cancel (Fill) and confirm (Ink, with an optional [icon]) side by side, 1 : 2; stacked,
 * confirm first, when the text is large enough to wrap them.
 */
@Composable
internal fun SheetActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
    icon: EmberIcons? = null,
) {
    val cancel = stringResource(R.string.cancel)
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EmberButton(confirmLabel, onConfirm, size = ButtonSize.Lg, icon = icon, enabled = confirmEnabled)
            EmberButton(cancel, onCancel, variant = ButtonVariant.Fill, size = ButtonSize.Lg)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmberButton(cancel, onCancel, Modifier.weight(1f), variant = ButtonVariant.Fill, size = ButtonSize.Lg)
            EmberButton(confirmLabel, onConfirm, Modifier.weight(2f), size = ButtonSize.Lg, icon = icon, enabled = confirmEnabled)
        }
    }
}

// The digits' own timing (Digits.kt): a changed digit's old glyph is gone 130 ms into its 520 ms
// hand-off, and a change that comes while one is still running swaps at once.
private const val DIGIT_SWAP_MS = 130
private const val DIGIT_HANDOFF_MS = 520

/**
 * The value a [NumberWithUnit] beside a caption is showing at this moment, so the caption never
 * contradicts it: "1,342 kcal · Total · 898 left of 2,240" adds up in every frame. It keeps the
 * digits' timing: [from] (what the number showed before it appeared) until [delayMillis] plus the
 * moment the old digits are gone; a later change at that same moment; and at once for a change that
 * follows the previous one within a hand-off (a held stepper, fast typing), as the digits then swap
 * at once too. Reduced motion: the value itself.
 */
@Composable
internal fun <T> rememberCaptionValue(value: T, from: T? = null, delayMillis: Int = 0): T {
    val reduced = Ember.motion.reduced
    var shown by remember { mutableStateOf(if (from != null && !reduced) from else value) }
    // Only the first hand-off (from [from]) waits for [delayMillis]; later changes start at once.
    val pendingDelay = remember { intArrayOf(if (from != null) delayMillis else 0) }
    // Until when (System.nanoTime) the digits are still busy with a hand-off.
    val busyUntil = remember { longArrayOf(Long.MIN_VALUE) }
    LaunchedEffect(value, reduced) {
        val wait = pendingDelay[0]
        pendingDelay[0] = 0
        if (value == shown) return@LaunchedEffect
        val now = System.nanoTime()
        if (reduced || now < busyUntil[0]) {
            shown = value
            busyUntil[0] = now + DIGIT_HANDOFF_MS * 1_000_000L
            return@LaunchedEffect
        }
        busyUntil[0] = now + (wait + DIGIT_HANDOFF_MS) * 1_000_000L
        motionDelay((wait + DIGIT_SWAP_MS).toLong())
        shown = value
    }
    return shown
}

/** "Today", or the day as a medium date ("Oct 9, 2026") for the day navigation. */
@Composable
private fun dayNavLabel(state: DiaryUiState): String {
    if (state.isToday) return stringResource(R.string.tab_today)
    val locale = LocalConfiguration.current.locales[0]
    return remember(state.epochDay, locale) {
        LocalDate.ofEpochDay(state.epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

/**
 * The eyebrow date, "Saturday 10 October" ("Субота, 10 жовтня"): weekday, day and month in the
 * order and punctuation of the device's language, first letter upper-cased.
 */
@Composable
internal fun rememberLongDate(day: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(day, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        day.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
}

/** Clock times ("13:02", or "1:02 PM") as the phone shows them: 24-hour or 12-hour by the user's setting. */
private class TimeFormat(private val formatter: DateTimeFormatter) {
    fun of(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(formatter)
}

@Composable
private fun rememberTimeFormat(): TimeFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, context) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        TimeFormat(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }
}

/** Maps each meal slot to its translated label. */
@Composable
fun mealSlotLabel(slot: MealSlot): String = stringResource(
    when (slot) {
        MealSlot.BREAKFAST -> R.string.meal_breakfast
        MealSlot.LUNCH -> R.string.meal_lunch
        MealSlot.DINNER -> R.string.meal_dinner
        MealSlot.SNACK -> R.string.meal_snack
    }
)
