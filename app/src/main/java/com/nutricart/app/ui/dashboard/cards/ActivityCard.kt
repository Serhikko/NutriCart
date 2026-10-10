package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nutricart.app.R
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.hcExerciseLabel
import com.nutricart.app.ui.common.workoutTypeLabel
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.WorkoutItem
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.Digits
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.StatTile
import com.nutricart.app.ui.ember.WholeWordsText

/**
 * Activity and workouts in one block, as the gallery shows them: the activity tiles, then the
 * workouts card. Today places the two as separate cards in its list ([ActivityTiles], [WorkoutsCard]).
 */
@Composable
internal fun ActivityCard(
    state: DashboardUiState,
    onAddWorkout: () -> Unit,
    onDeleteWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityTiles(state)
        WorkoutsCard(state, onAddWorkout, onDeleteWorkout)
    }
}

/**
 * What the watch measured today: Steps and Active calories as two tiles with where and when the
 * numbers came from ("Health Connect · 16:52"), then Exercise, Sleep and Heart rate as three
 * compact tiles; "—" where there is no data. At large font sizes the three become one column.
 */
@Composable
internal fun ActivityTiles(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val t = Ember.type
    val dash = stringResource(R.string.no_data_dash)
    // The time stays on one line ("8:01 AM"); the footnote may wrap before it.
    val source = state.lastSyncEpochMillis?.let { stringResource(R.string.tile_source, rememberClockTime(it).replace(' ', '\u00A0')) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.steps_label),
                icon = EmberIcons.Steps,
                metric = Metric.Steps,
                value = {
                    val steps = state.steps
                    if (steps != null) Digits(steps.toLong(), t.stat) else Dash(dash)
                },
                footnote = source?.takeIf { state.steps != null },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                label = stringResource(R.string.active_kcal_label),
                icon = EmberIcons.Flame,
                metric = Metric.Kcal,
                value = {
                    val active = state.activeKcal
                    if (active != null) {
                        NumberWithUnit(active.toLong(), stringResource(R.string.kcal_unit), t.stat, unitSize = 14.sp)
                    } else {
                        Dash(dash)
                    }
                },
                footnote = source?.takeIf { state.activeKcal != null },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        val exercise = state.exerciseMinutes?.let { stringResource(R.string.minutes_value, it) } ?: dash
        val sleep = state.sleepMinutes?.let { stringResource(R.string.sleep_value, it / 60, it % 60) } ?: dash
        val heart = state.avgHeartRateBpm?.let { stringResource(R.string.bpm_value, it) } ?: dash
        val tiles: @Composable (Modifier) -> Unit = { m ->
            CompactTile(stringResource(R.string.exercise_label), EmberIcons.Clock, c.carbsInk, exercise, m)
            CompactTile(stringResource(R.string.sleep_label), EmberIcons.Moon, c.proteinInk, sleep, m)
            CompactTile(stringResource(R.string.heart_rate_label), EmberIcons.Heart, c.danger, heart, m)
        }
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** "—" where a tile has no number yet, in the number's place and size. */
@Composable
private fun Dash(dash: String) {
    BasicText(dash, style = Ember.type.stat.copy(color = Ember.colors.label3))
}

/**
 * A small tile for a secondary measure: a 16 dp glyph and the label in the measure's ink, the value
 * (its unit from the string, drawn smaller) at the bottom. One TalkBack node.
 */
@Composable
private fun CompactTile(label: String, icon: EmberIcons, ink: Color, value: String, modifier: Modifier) {
    val t = Ember.type
    EmberCard(modifier, padding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 11.dp), mergeDescendants = true) {
        // The glyph sits on the label's first line, also when the label wraps ("Avg heart / rate").
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            EmberIcon(icon, null, Modifier.padding(top = 1.dp), size = 16.dp, tint = ink)
            // Wraps between words only: a narrow tile shrinks "Тренування" a little rather than split it.
            WholeWordsText(label, t.footnote.copy(fontWeight = FontWeight.SemiBold, color = ink, lineBreak = LineBreak.Heading))
        }
        Spacer(Modifier.height(6.dp))
        // When a neighbour's label wraps, the slack goes above the value so the numbers line up.
        Spacer(Modifier.weight(1f))
        ValueText(value, t.kpi.copy(fontSize = 22.sp), color = if (value == stringResource(R.string.no_data_dash)) Ember.colors.label3 else Color.Unspecified)
    }
}

/**
 * Today's workouts: the watch's sessions and the ones added by hand, each with its glyph, name,
 * "32 min · watch" and kcal; manual ones carry a delete button (deleting is immediate, as before).
 * "+ Add workout" in the head opens the workout sheet.
 */
@Composable
internal fun WorkoutsCard(
    state: DashboardUiState,
    onAddWorkout: () -> Unit,
    onDeleteWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val t = Ember.type
    EmberCard(modifier, padding = PaddingValues(top = 18.dp, bottom = 8.dp)) {
        CardHead(
            stringResource(R.string.workouts_section_title),
            Modifier.padding(horizontal = 18.dp),
            icon = EmberIcons.Dumbbell,
            action = { PlainLink(stringResource(R.string.workout_add), onAddWorkout, icon = EmberIcons.Plus) },
        )
        if (state.workouts.isEmpty()) {
            BasicText(
                stringResource(R.string.workouts_empty),
                Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
                style = t.footnote.copy(fontSize = 15.sp, color = c.label2),
            )
        } else {
            state.workouts.forEachIndexed { i, item -> WorkoutRow(item, divided = i > 0, onDelete = onDeleteWorkout) }
        }
    }
}

/** One workout: glyph tile, name over "amount · source", kcal (or "—"), and delete for manual rows. */
@Composable
private fun WorkoutRow(item: WorkoutItem, divided: Boolean, onDelete: (Long) -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    // Manual rows have a catalog type; watch rows use their own title, or a name mapped from the
    // raw Health Connect exercise type when there is none.
    val name = when {
        item.type != null -> stringResource(workoutTypeLabel(item.type))
        !item.title.isNullOrBlank() -> item.title
        else -> stringResource(hcExerciseLabel(item.hcExerciseType))
    }
    val amount = item.minutes?.let { stringResource(R.string.minutes_value, it) }
        ?: item.reps?.let { stringResource(R.string.workout_reps_value, it) }
    val source = stringResource(if (item.isFromWatch) R.string.workout_source_watch else R.string.workout_source_manual)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (divided) Modifier.topHairline(c.sep, 18.dp + 46.dp, 18.dp) else Modifier)
            .padding(start = 18.dp, end = if (item.isFromWatch) 18.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name, amount and kcal read as one stop; the delete button stays its own.
        Row(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(34.dp).background(c.fill2, EmberShapes.tileIcon), contentAlignment = Alignment.Center) {
                EmberIcon(workoutGlyph(item), null, size = 19.dp, tint = c.label)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(name, style = t.callout.copy(color = c.label))
                BasicText(listOfNotNull(amount, source).joinToString(" · "), style = t.footnote.copy(color = c.label2))
            }
            val kcal = item.kcal
            if (kcal != null) {
                NumberWithUnit(kcal.toLong(), stringResource(R.string.kcal_unit), t.rowNumber, unitSize = 12.sp)
            } else {
                BasicText(stringResource(R.string.no_data_dash), style = t.rowNumber.copy(color = c.label2))
            }
        }
        if (!item.isFromWatch) {
            EmberIconButton(
                EmberIcons.Trash,
                stringResource(R.string.workout_delete),
                onClick = { onDelete(item.id) },
                style = IconButtonStyle.Plain,
                size = 40.dp,
            )
        }
    }
}

/** On foot: the steps glyph; everything else: the dumbbell. */
private fun workoutGlyph(item: WorkoutItem): EmberIcons = when {
    item.type == WorkoutType.RUNNING || item.type == WorkoutType.TREADMILL_WALK -> EmberIcons.Steps
    item.hcExerciseType in OnFoot -> EmberIcons.Steps
    else -> EmberIcons.Dumbbell
}

private val OnFoot = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
)
