package com.nutricart.app.ui.quickadd

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.logic.MealSlotGuess
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.AddWeightDialog
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberDurations
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.pressScale
import com.nutricart.app.ui.ember.rememberSheetCloser
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The "+" sheet: log anything from any tab without losing your place.
 *
 * Food and the scanner NAVIGATE (they need a whole screen); water, a workout
 * and the weight are written from here. Everything here targets TODAY — the
 * date under the title says so out loud, because the diary can be sitting on
 * another day while this sheet is open.
 */
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onLogFood: (MealSlot) -> Unit,
    onScanFood: (MealSlot) -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val weightKg by viewModel.weightKg.collectAsState()
    val today = remember { LocalDate.now().toEpochDay() }
    // remember: a fresh Flow on every recomposition would restart the query.
    // initial = null so the row can stay blank for the frame before the real
    // total arrives, instead of showing 0 ml and animating up to it.
    val waterFlow = remember(today) { viewModel.observeWater(today) }
    val waterMl by waterFlow.collectAsState(initial = null)

    QuickAddSheetContent(
        today = today,
        waterMl = waterMl,
        weightKg = weightKg,
        onDismiss = onDismiss,
        onLogFood = onLogFood,
        onScanFood = onScanFood,
        onAddWater = viewModel::addWater,
        onUndoWater = viewModel::undoWater,
        onAddWorkout = viewModel::addWorkout,
        onLogWeight = viewModel::logWeight,
    )
}

/**
 * The stateless half of [QuickAddSheet]. [today] is the day everything is
 * written to; [waterMl] null = today's total has not arrived yet.
 *
 * An Ember sheet over whichever tab is showing (that tab recedes behind it):
 * the meal chips, two big tiles for food (search or scan, into the chosen
 * meal), the day's water with its glasses, and a short list for a workout and
 * the weight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheetContent(
    today: Long,
    waterMl: Int?,
    weightKg: Double,
    onDismiss: () -> Unit,
    onLogFood: (MealSlot) -> Unit,
    onScanFood: (MealSlot) -> Unit,
    onAddWater: (ml: Int) -> Unit,
    onUndoWater: () -> Unit,
    onAddWorkout: (type: WorkoutType, amount: Int) -> Unit,
    onLogWeight: (kg: Double) -> Unit,
) {
    // The sheet always opens fully (no half-height stop: the last rows would open below the screen
    // edge, and a drag there moves the sheet instead of scrolling it). Every action that leaves runs
    // the sheet's slide-away first and only then lets the caller drop it or navigate — otherwise the
    // sheet vanishes in a frame, or floats over the next screen.
    val closer = rememberSheetCloser()

    // The clock only PRESELECTS the meal; the chips sit right above the two
    // food tiles, so a night-shift dinner is one tap away from correct.
    var slot by rememberSaveable { mutableStateOf(MealSlotGuess.forTime(LocalTime.now())) }
    var showWorkout by rememberSaveable { mutableStateOf(false) }
    var showWeight by rememberSaveable { mutableStateOf(false) }

    val title = stringResource(R.string.quick_add_title)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        SheetHeader(
            title,
            subtitle = longDate(today),
            onClose = { closer.close(onDismiss) },
            closeLabel = stringResource(R.string.cancel),
        )

        val slots = MealSlot.entries
        ChipGroup(
            options = slots.map { mealSlotLabel(it) },
            selected = setOf(slots.indexOf(slot)),
            onToggle = { slot = slots[it] },
            modifier = Modifier.padding(top = 2.dp),
        )

        val meal = mealSlotLabel(slot)
        FoodTiles(
            meal = meal,
            // The meal is taken at the tap, not after the slide-away.
            onLogFood = { val chosen = slot; closer.close { onLogFood(chosen) } },
            onScanFood = { val chosen = slot; closer.close { onScanFood(chosen) } },
            modifier = Modifier.padding(top = 2.dp),
        )

        WaterBlock(waterMl, onAddWater, onUndoWater)

        MoreList(
            weightKg = weightKg,
            onWorkout = { showWorkout = true },
            onWeight = { showWeight = true },
        )
    }

    // Declared OUTSIDE the sheet: a sheet nested in the quick-add sheet's
    // content would vanish together with it. Each rises over this sheet;
    // writing closes both (that one first, then this one).
    if (showWorkout) {
        AddWorkoutDialog(
            weightKg = weightKg,
            onConfirm = { type, amount ->
                onAddWorkout(type, amount)
                showWorkout = false
                closer.close(onDismiss)
            },
            onDismiss = { showWorkout = false },
        )
    }
    if (showWeight) {
        AddWeightDialog(
            currentWeightKg = weightKg,
            onConfirm = { kg ->
                onLogWeight(kg)
                showWeight = false
                closer.close(onDismiss)
            },
            onDismiss = { showWeight = false },
        )
    }
}

/**
 * The day everything in this sheet is written to, the way the Today title's eyebrow writes it:
 * "Saturday, October 10" / "Субота, 10 жовтня".
 */
@Composable
private fun longDate(epochDay: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(epochDay, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
}

/**
 * "Log food" and "Scan a barcode" as two tiles side by side, each saying which meal it fills. At
 * large text sizes they stack, so the labels keep their words whole.
 */
@Composable
private fun FoodTiles(meal: String, onLogFood: () -> Unit, onScanFood: () -> Unit, modifier: Modifier = Modifier) {
    val log = stringResource(R.string.quick_add_food)
    val scan = stringResource(R.string.scan_barcode)
    if (LocalDensity.current.fontScale < 1.5f) {
        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FoodTile(EmberIcons.Search, log, meal, onLogFood, Modifier.weight(1f).fillMaxHeight())
            FoodTile(EmberIcons.Scan, scan, meal, onScanFood, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FoodTile(EmberIcons.Search, log, meal, onLogFood, Modifier.fillMaxWidth(), wide = true)
            FoodTile(EmberIcons.Scan, scan, meal, onScanFood, Modifier.fillMaxWidth(), wide = true)
        }
    }
}

/**
 * One food tile: a 38 dp ink square with the Ember glyph, the action, and the meal under it. The
 * whole tile is one button ("Log food, Dinner"); it darkens a little and presses in. [wide] (the
 * stacked tiles at large text sizes) puts the glyph beside the words instead of above them.
 */
@Composable
private fun FoodTile(
    icon: EmberIcons,
    label: String,
    meal: String,
    onClick: () -> Unit,
    modifier: Modifier,
    wide: Boolean = false,
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val tile = modifier
        .pressScale(source, .97f)
        .clip(EmberShapes.nested)
        .background(c.surface2)
        .clickable(
            interactionSource = source,
            indication = EmberIndication(EmberShapes.nested, c.focus, pressed = c.fill),
            role = Role.Button,
            onClick = onClick,
        )
        .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 13.dp)
    val glyph = @Composable {
        Box(
            Modifier
                .size(38.dp)
                .background(c.ink, EmberShapes.glyph),
            contentAlignment = Alignment.Center,
        ) {
            EmberIcon(icon, null, size = 21.dp, brush = EmberBrushes.emberIcon(c), strokeWidth = 2.2f)
        }
    }
    if (wide) {
        Row(tile, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            glyph()
            FoodTileText(label, meal)
        }
    } else {
        Column(tile, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            glyph()
            FoodTileText(label, meal)
        }
    }
}

/** The tile's action and, under it, the meal it fills. */
@Composable
private fun FoodTileText(label: String, meal: String) {
    val c = Ember.colors
    val t = Ember.type
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ControlText(
            label,
            style = t.callout.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em),
            color = c.label,
        )
        // A new meal chip swaps the line underneath: the new name drops in, the old one fades.
        val reduced = Ember.motion.reduced
        AnimatedContent(
            targetState = meal,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(EmberDurations.State, easing = EmberEasing.Out)) +
                        slideInVertically(tween(EmberDurations.State, easing = EmberEasing.Out)) { -it / 3 })
                        .togetherWith(fadeOut(tween(120, easing = EmberEasing.In)))
                        .using(SizeTransform(clip = false))
                }
            },
            label = "meal",
        ) { name ->
            ControlText(
                name,
                style = t.footnote.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                color = c.label2,
            )
        }
    }
}

/** One glass is 250 ml; eight glasses are the picture of a day's water (as on Today). */
private const val GlassMl = 250
private const val Glasses = 8

/**
 * Today's water: the total (its digits hand off as it changes, "—" until it has loaded), eight
 * glasses, "+250 ml", "+500 ml" and Undo (off at 0). It writes at once and the sheet stays: a tap
 * fills the next glass on the bouncy spring, Undo drains the last one.
 */
@Composable
private fun WaterBlock(waterMl: Int?, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    val ml = stringResource(R.string.ml_unit)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(EmberShapes.nested)
            .background(c.surface2)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
    ) {
        CardHead(stringResource(R.string.water_label), icon = EmberIcons.Drop, metric = Metric.Water)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            val numberStyle = t.stat.copy(fontSize = 26.sp)
            if (waterMl == null) {
                // Not loaded yet: a dash, not "0 ml", so nothing counts up from zero a frame later.
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        stringResource(R.string.no_data_dash),
                        style = numberStyle,
                        color = { c.label2 },
                        modifier = Modifier.alignByBaseline(),
                    )
                    BasicText(
                        ml,
                        style = t.subhead,
                        color = { c.label2 },
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 4.dp),
                    )
                }
            } else {
                NumberWithUnit(waterMl.toLong(), ml, numberStyle, unitSize = 15.sp)
            }
            Row(
                Modifier
                    .padding(bottom = 1.dp)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (i in 0 until Glasses) Glass(full = (waterMl ?: 0) >= (i + 1) * GlassMl)
            }
        }
        // The units come from the strings: "+250" and "ml", kept together.
        val add250 = stringResource(R.string.water_add_250) + "\u00A0" + ml
        val add500 = stringResource(R.string.water_add_500) + "\u00A0" + ml
        ButtonLine(Modifier.padding(top = 10.dp)) {
            EmberButton(add250, { onAdd(250) }, variant = ButtonVariant.Water)
            EmberButton(add500, { onAdd(500) }, variant = ButtonVariant.Water)
            EmberButton(
                stringResource(R.string.water_undo),
                onUndo,
                variant = ButtonVariant.Plain,
                enabled = (waterMl ?: 0) > 0,
            )
        }
    }
}

/**
 * Buttons on one line at their full size, the last one (Undo) pushed to the far end. When they do
 * not all fit (a long Ukrainian "Скасувати", large text) they wrap instead, line by line from the
 * start, so no label ever has to shrink.
 */
@Composable
private fun ButtonLine(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val lines = ArrayList<MutableList<Placeable>>()
        var used = 0
        for (p in placeables) {
            val line = lines.lastOrNull()
            if (line == null || used + gap + p.width > width) {
                lines += mutableListOf(p)
                used = p.width
            } else {
                line += p
                used += gap + p.width
            }
        }
        val heights = lines.map { line -> line.maxOf { it.height } }
        layout(width, heights.sum() + gap * (lines.size - 1).coerceAtLeast(0)) {
            var y = 0
            lines.forEachIndexed { i, line ->
                var x = 0
                line.forEachIndexed { j, p ->
                    // All on one line: the last button sits at the end of it.
                    val atEnd = lines.size == 1 && j == line.lastIndex && j > 0
                    p.placeRelative(if (atEnd) width - p.width else x, y + (heights[i] - p.height) / 2)
                    x += p.width + gap
                }
                y += heights[i] + gap
            }
        }
    }
}

// The glass and its water, drawn on a 19 × 28 grid (the web's glass, the same as Today's card).
private val GlassPath = PathParser().parsePathString("M1.5 2.5h16l-1.7 22a2 2 0 0 1-2 1.9H5.2a2 2 0 0 1-2-1.9z").toPath()
private val WaterPath = PathParser()
    .parsePathString("M3.1 9.5c2 1.2 4.2 1.2 6.4 0s4.4-1.2 6.4 0l-1.2 15a1.6 1.6 0 0 1-1.6 1.5H5.9a1.6 1.6 0 0 1-1.6-1.5z")
    .toPath()

/**
 * One glass: a `fill2` body with a hairline rim, its water a blue gradient. It opens as it is (the
 * sheet is not a first open); a tap fills it from the bottom on the bouncy spring, Undo drains it in
 * 220 ms. Decorative: the total next to it says how much.
 */
@Composable
private fun Glass(full: Boolean) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val level = remember { Animatable(if (full) 1f else 0f) }
    LaunchedEffect(full, reduced) {
        val goal = if (full) 1f else 0f
        when {
            level.value == goal -> Unit
            reduced -> level.snapTo(goal)
            full -> level.animateTo(1f, EmberSprings.bouncy())
            else -> level.animateTo(0f, tween(220, easing = EmberEasing.In))
        }
    }
    // In the glass's own 19 × 28 units: the gradient runs from the water's top to the glass's bottom.
    val water = Brush.verticalGradient(listOf(c.water2, c.water), startY = 9.5f, endY = 26f)
    Spacer(
        Modifier
            .size(19.dp, 28.dp)
            .drawBehind {
                scale(size.width / 19f, size.height / 28f, pivot = Offset.Zero) {
                    drawPath(GlassPath, c.fill2)
                    val l = level.value
                    // The water rises from the bottom of the glass (y = 26).
                    if (l > 0f) scale(1f, l, pivot = Offset(9.5f, 26f)) { drawPath(WaterPath, water) }
                    drawPath(GlassPath, c.sepStrong, style = Stroke(1.1f))
                }
            },
    )
}

/**
 * "Add workout" and "Weight" (with the latest value) as two rows of one rounded group; each opens
 * its own sheet over this one.
 */
@Composable
private fun MoreList(weightKg: Double, onWorkout: () -> Unit, onWeight: () -> Unit) {
    val c = Ember.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(EmberShapes.nested)
            .background(c.surface2),
    ) {
        ListRow(
            title = stringResource(R.string.workout_add),
            leading = { RowIcon(EmberIcons.Dumbbell, c.label) },
            onClick = onWorkout,
            chevron = true,
            minHeight = 52.dp,
        )
        // The hairline starts where the titles do (16 + 30 dp tile + 12).
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 58.dp)
                .height(.5.dp)
                .background(c.sep),
        )
        ListRow(
            title = stringResource(R.string.weight_card_title),
            leading = { RowIcon(EmberIcons.Scale, c.weightInk) },
            trailing = if (weightKg > 0.0) {
                {
                    ControlText(
                        stringResource(R.string.weight_kg_value, weightKg),
                        style = Ember.type.callout,
                        color = c.label2,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                null
            },
            onClick = onWeight,
            chevron = true,
            minHeight = 52.dp,
        )
    }
}

/** A row's 30 dp `fill` tile with its glyph. */
@Composable
private fun RowIcon(icon: EmberIcons, tint: Color) {
    Box(
        Modifier
            .size(30.dp)
            .background(Ember.colors.fill, EmberShapes.rowIcon),
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(icon, null, size = 19.dp, tint = tint)
    }
}
