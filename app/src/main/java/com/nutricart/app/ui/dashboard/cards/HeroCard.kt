package com.nutricart.app.ui.dashboard.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberSharedBounds
import com.nutricart.app.ui.ember.rememberHeroRingSize
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.ember.rememberLastShown
import com.nutricart.app.ui.ember.rememberLastShownValue
import com.nutricart.app.ui.ember.ringEntrance
import com.nutricart.app.ui.ember.ringHalo
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs

/**
 * "In the zone": eaten between 90% of the target and the 5% a day may run over
 * (AdherenceCalculator's tolerance), the band the web's zone badge uses.
 */
private val ZoneBand = .90f..1.05f

/**
 * The centrepiece of Today (no card): the Ember day ring with what is left in large numerals
 * ("855 kcal left", or "290 kcal over" with the ring's ink second lap past the goal), the zone
 * badge, the three KPIs (Eaten · Daily target · Calories out) and the activity note.
 *
 * Motion: on the first open of the day the ring scales in and sweeps, the digits arrive one by one,
 * the glow blooms and one pass of light runs round the ring; landing in the zone pulses the glow
 * twice and pops the badge. Later visits land on the values, except that anything that changed while
 * Today was away (back from Add) sweeps and hands off from what it showed before. TalkBack reads the
 * ring as one sentence ("1,385 of 2,240 kcal eaten, 855 left") and the KPIs as one row.
 */
@Composable
internal fun HeroRing(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    entrances: EntranceState? = null,
) {
    val targets = state.targets ?: return
    val c = Ember.colors
    val t = Ember.type
    val nf = rememberIntegerFormat()
    // Read on every composition: after midnight the next refresh moves everything to the new day.
    val day = LocalDate.now().toEpochDay()
    val target = targets.kcal
    val eaten = state.eatenKcal
    val remaining = state.remainingKcal
    val over = remaining < 0

    // What the ring and the numbers last showed anywhere in the app (Today left composition while
    // Food search was open): a change since then sweeps and hands off from there.
    val lastRing = rememberLastShown("ring/$day", eaten.toFloat())
    val lastRemaining = rememberLastShownValue("remaining/$day", remaining.toLong())
    val lastEaten = rememberLastShownValue("eaten/$day", eaten.toLong())
    val from = if (first) null else lastRing?.takeIf { it != eaten.toFloat() }

    val ratio = if (target > 0) eaten / target.toFloat() else 0f
    val inZone = target > 0 && ratio in ZoneBand
    // The zone moment plays when the ring lands in the zone: the first open of the day, or a change
    // that crossed 90% since the ring was last shown. Decided once; the badge simply stays after.
    val zoneMoment = remember {
        inZone && (first || (from != null && target > 0 && from / target < ZoneBand.start))
    }

    val dayLine = buildString {
        append(
            if (over) {
                stringResource(R.string.ring_day_line_over, nf.format(eaten), nf.format(target), nf.format(-remaining.toLong()))
            } else {
                stringResource(R.string.ring_day_line, nf.format(eaten), nf.format(target), nf.format(remaining.toLong()))
            },
        )
        if (inZone) append(", ").append(stringResource(R.string.day_zone))
    }

    val ring = rememberHeroRingSize()
    Column(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.ringHalo(bloom = first), contentAlignment = Alignment.Center) {
            Ring(
                value = eaten.toFloat(),
                target = target.toFloat(),
                size = ring.size,
                stroke = ring.stroke,
                modifier = Modifier.emberSharedBounds("day-ring/$day").ringEntrance(first),
                from = from,
                sweep = first || from != null,
                delayMillis = if (from != null) 160 else 140,
                shineAtMillis = when {
                    zoneMoment -> 1250
                    first -> 700
                    else -> null
                },
                zone = zoneMoment,
                contentDescription = dayLine,
            ) {
                Column(
                    Modifier.padding(horizontal = ring.size * (30f / 252f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The ring's centre has a fixed room: the numeral shrinks to fit it, the words
                    // under it grow with the font size only so far.
                    val numeral = t.heroNumeral.copy(fontSize = t.heroNumeral.fontSize.cappedAt(78.dp))
                    FittedNumber(
                        value = abs(remaining).toLong(),
                        numberStyle = numeral,
                        enter = first,
                        delayMillis = if (first) 200 else 240,
                        // A hand-off needs the same digit count: when the count changes (1,010 → 855)
                        // Digits would empty the centre and re-enter, so the number simply stands
                        // while the ring sweep carries the change.
                        from = if (first) null else lastRemaining?.let { abs(it) }?.takeIf { sameShape(nf, it, abs(remaining).toLong()) },
                        color = c.label,
                    )
                    BasicText(
                        stringResource(if (over) R.string.day_kcal_over else R.string.day_kcal_left),
                        modifier = Modifier
                            .widthIn(max = 132.dp)
                            .emberEntrance(0, EntranceKind.FadeUp, first, entrances, "hero-caption"),
                        style = t.subhead.copy(
                            fontSize = t.subhead.fontSize.cappedAt(20.dp),
                            lineHeight = 1.25.em,
                            color = c.label2,
                            textAlign = TextAlign.Center,
                            lineBreak = LineBreak.Heading,
                        ),
                    )
                    if (inZone) ZoneBadge(pop = zoneMoment)
                }
            }
        }
        HeroKpis(
            eaten = eaten,
            target = target,
            out = state.caloriesOut,
            eatenFrom = if (first) null else lastEaten?.takeIf { sameShape(nf, it, eaten.toLong()) },
            modifier = Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "hero-kpis"),
        )
        HeroNote(state, Modifier.emberEntrance(1, EntranceKind.FadeUp, first, entrances, "hero-note"))
    }
}

/** Two numbers print with the same digits-and-separators pattern ("940" and "855", not "1,010" and "855"). */
private fun sameShape(nf: NumberFormat, a: Long, b: Long): Boolean {
    fun shape(v: Long) = nf.format(v).map { if (it.isDigit()) '0' else it }
    return shape(a) == shape(b)
}

/**
 * The ink capsule with an Ember check under "N kcal left" while the day is in the zone. It pops
 * (bouncy, after the sweep) at the zone moment and just sits there afterwards; with "Remove
 * animations" it fades in.
 */
@Composable
private fun ZoneBadge(pop: Boolean) {
    val c = Ember.colors
    val t = Ember.type
    val reduced = Ember.motion.reduced
    val shown = remember { Animatable(if (pop) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!pop) return@LaunchedEffect
        if (reduced) shown.animateTo(1f, tween(200, easing = EmberEasing.Out))
        else shown.animateTo(1f, tween(620, 1150, EmberEasing.Bouncy))
    }
    Row(
        Modifier
            .padding(top = 8.dp)
            .graphicsLayer {
                val e = shown.value
                alpha = e.coerceIn(0f, 1f)
                if (!reduced) {
                    scaleX = .4f + .6f * e
                    scaleY = .4f + .6f * e
                }
            }
            .background(c.ink, EmberShapes.capsule)
            .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EmberIcon(EmberIcons.Check, null, size = 15.dp, brush = EmberBrushes.emberIcon(c), strokeWidth = 3f)
        BasicText(
            stringResource(R.string.day_zone),
            style = t.footnote.copy(
                fontSize = t.footnote.fontSize.cappedAt(17.dp),
                fontWeight = FontWeight.SemiBold,
                color = c.onInk,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Eaten · Daily target · Calories out, split by hairlines, read by TalkBack as one row. At large
 * font sizes, where three columns cannot hold "Calories out", they become three label–value lines.
 */
@Composable
private fun HeroKpis(eaten: Int, target: Int, out: Int, eatenFrom: Long?, modifier: Modifier = Modifier) {
    val kpis = listOf(
        Triple(stringResource(R.string.eaten_label), eaten.toLong(), eatenFrom),
        Triple(stringResource(R.string.dashboard_target), target.toLong(), null),
        Triple(stringResource(R.string.calories_out_label), out.toLong(), null),
    )
    val c = Ember.colors
    val t = Ember.type
    val unit = stringResource(R.string.kcal_unit)
    val labelStyle = t.footnote.copy(fontWeight = FontWeight.Medium, color = c.label2, textAlign = TextAlign.Center)
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier.fillMaxWidth().padding(top = 14.dp).semantics(mergeDescendants = true) {}) {
            kpis.forEachIndexed { i, (label, value, from) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i > 0) Modifier.topHairline(c.sep, 0.dp) else Modifier)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText(label, Modifier.weight(1f), style = labelStyle.copy(textAlign = TextAlign.Start))
                    NumberWithUnit(value, unit, t.kpi, from = from, delayMillis = 300, unitSize = 12.sp)
                }
            }
        }
        return
    }
    Row(
        modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(top = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
    ) {
        kpis.forEachIndexed { i, (label, value, from) ->
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (i > 0) {
                            Modifier.drawBehind {
                                val w = .5.dp.toPx()
                                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w / 2 else w / 2
                                drawLine(c.sepStrong, Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), w)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                BasicText(label, style = labelStyle.copy(lineBreak = LineBreak.Heading))
                NumberWithUnit(value, unit, t.kpi, from = from, delayMillis = 300, unitSize = 12.sp)
            }
        }
    }
}

/**
 * The one note under the KPIs, most useful first: "+N kcal" covers watch AND manual workouts; the
 * watch note stays for the rare measured-zero day (bonus 0, but the formula did switch to watch mode).
 */
@Composable
private fun HeroNote(state: DashboardUiState, modifier: Modifier = Modifier) {
    val text = when {
        state.activityBonusKcal > 0 -> stringResource(R.string.target_activity_bonus, state.activityBonusKcal)
        state.adjustedByActivity -> stringResource(R.string.target_adjusted_note)
        else -> return
    }
    val c = Ember.colors
    // The flame is part of the text, so it stays at the start of the first line when the note wraps.
    val flame = "flame"
    val annotated = buildAnnotatedString {
        // The glyph is decorative: its stand-in text is a space, so TalkBack reads only the note.
        appendInlineContent(flame, " ")
        append("\u2002")
        append(text)
    }
    val inline = mapOf(
        flame to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            EmberIcon(EmberIcons.Flame, null, Modifier.fillMaxSize(), size = 15.dp, brush = EmberBrushes.emberIcon(c), strokeWidth = 2.4f)
        },
    )
    BasicText(
        annotated,
        modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
        style = Ember.type.footnote.copy(color = c.label2, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading),
        inlineContent = inline,
    )
}
