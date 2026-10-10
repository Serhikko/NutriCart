package com.nutricart.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.data.local.dao.FoodContribution
import com.nutricart.app.domain.logic.AdherenceCalculator
import com.nutricart.app.domain.logic.DietInsights
import com.nutricart.app.ui.dashboard.cards.ValueText
import com.nutricart.app.ui.dashboard.cards.bestPattern
import com.nutricart.app.ui.dashboard.cards.capitalized
import com.nutricart.app.ui.dashboard.cards.shortWeekday
import com.nutricart.app.ui.dashboard.cards.topHairline
import com.nutricart.app.ui.ember.Bar
import com.nutricart.app.ui.ember.CaloriesChart
import com.nutricart.app.ui.ember.CaloriesChartLegend
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ChartDay
import com.nutricart.app.ui.ember.ChartHeader
import com.nutricart.app.ui.ember.DayMarker
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.MarkerLegend
import com.nutricart.app.ui.ember.MarkerState
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberIntegerFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The "how am I doing over time" body of the Today screen: the daily average, calories by day,
 * the adherence calendar, honest averages and the diet analysis.
 *
 * It owns no chrome — the screen above it draws the large title and the range control and hands
 * the chosen [range] down.
 */
@Composable
fun StatsBody(range: StatsRange, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    // Loads on entry, on every range change, and on every resume WHILE THIS
    // BODY IS ON SCREEN. The last part matters twice: it rolls the window over
    // at midnight, and it picks up a watch sync that just landed. It still
    // costs nothing for someone sitting on the "Today" segment — this composable
    // does not exist then, so the 90-day queries never run for them.
    LifecycleResumeEffect(range) {
        viewModel.selectRange(range)
        onPauseOrDispose { }
    }

    StatsContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
    )
}

/**
 * The stateless half of [StatsBody]: draws [state], forwards the month arrows. It is one column of
 * blocks (the Today screen's list holds it as one item): the hero (no card), then the cards 12 dp
 * apart. Week shows the chart before the calendar, Month and 90 days the calendar first.
 *
 * The layout follows the data on screen, not the requested range: while a new range loads, the old
 * one stays whole, then everything switches at once (the average hands its digits off, the week's
 * markers fold away, the chart rises anew on that range's first open of the day).
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    if (state.loading) {
        StatsSkeleton()
        return
    }
    val range = StatsRange.entries.firstOrNull { it.days == state.bars.size } ?: state.range
    val first = rememberFirstOpen(
        when (range) {
            StatsRange.WEEK -> "stats-week"
            StatsRange.MONTH -> "stats-month"
            StatsRange.NINETY -> "stats-90"
        },
    )
    val entrances = rememberEntranceState()
    val logged = state.loggedDays > 0
    fun Modifier.rise(index: Int, key: String) = emberEntrance(index, EntranceKind.Rise, first, entrances, key)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (logged) {
            // 22 dp from the hero to the first card: the column's 12 plus 10.
            StatsHero(state, range, first, entrances, Modifier.padding(bottom = 10.dp))
        }
        val chart: @Composable (Int) -> Unit = { index ->
            if (logged) {
                // A new range is a new chart: its bars rise again on that range's first open.
                key(range) { ChartCard(state, range, first, Modifier.rise(index, "chart")) }
            } else {
                // Nothing logged in the period: one calm empty state in the chart's place.
                EmberCard(Modifier.rise(index, "chart"), padding = PaddingValues(0.dp)) {
                    EmptyState(stringResource(R.string.stats_no_data), art = { EmptyDaysArt() })
                }
            }
        }
        if (range == StatsRange.WEEK) {
            chart(0)
            CalendarCard(state, onPreviousMonth, onNextMonth, first, Modifier.rise(1, "calendar"))
        } else {
            CalendarCard(state, onPreviousMonth, onNextMonth, first, Modifier.rise(0, "calendar"))
            chart(1)
        }
        AveragesCard(state, Modifier.rise(2, "averages"))
        if (logged) AnalysisCard(state, Modifier.rise(3, "analysis"))
    }
}

// ------------------------------------------------------------------------------------------------
// Hero
// ------------------------------------------------------------------------------------------------

/**
 * "Daily average" in large gradient numerals over the logged days, the week's seven day markers
 * (Week only; they fold away on the longer ranges) and "5 of 6 days on plan · target 2,240 kcal".
 */
@Composable
private fun StatsHero(state: StatsUiState, range: StatsRange, first: Boolean, entrances: EntranceState, modifier: Modifier) {
    val c = Ember.colors
    val t = Ember.type
    val nf = rememberIntegerFormat()
    val reduced = Ember.motion.reduced
    val glow = remember { Animatable(if (first && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (first && !reduced) glow.animateTo(1f, tween(1300, 180, EmberEasing.Out))
    }
    // While the markers fold away (Week → Month) they keep showing the week, not the new range's days.
    val week = remember { arrayOf(state) }
    if (range == StatsRange.WEEK) week[0] = state
    Column(
        modifier
            .fillMaxWidth()
            // The stage light: a soft Ember glow behind the numerals, never a blur. It is flattened so
            // it stays behind the hero and never tints the range control above it, and it blooms in
            // on the first open.
            .drawBehind {
                val v = glow.value
                if (v <= 0f) return@drawBehind
                val center = Offset(size.width * .3f, size.height * .5f)
                val radius = size.width * .75f
                scale(.6f + .4f * v, (.6f + .4f * v) * .62f, pivot = center) {
                    drawCircle(
                        Brush.radialGradient(0f to c.emberGlow, .72f to Color.Transparent, center = center, radius = radius),
                        radius = radius,
                        center = center,
                        alpha = v.coerceIn(0f, 1f),
                    )
                }
            }
            .padding(top = 4.dp, bottom = 6.dp),
    ) {
        BasicText(
            stringResource(R.string.week_average),
            Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "average-label"),
            style = t.subhead.copy(color = c.label2),
        )
        FittedNumber(
            value = state.avgEatenKcal.toLong(),
            numberStyle = t.weekHero,
            modifier = Modifier.padding(top = 2.dp).offset(x = (-2).dp),
            unit = stringResource(R.string.kcal_unit),
            minFontSize = 40.sp,
            enter = first,
            delayMillis = 170,
            gradient = true,
            unitSize = 20.sp,
        )
        AnimatedVisibility(
            visible = range == StatsRange.WEEK,
            enter = fadeIn(tween(360)) + expandVertically(tween(420)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(300)),
        ) {
            WeekMarkers(week[0], first, Modifier.padding(top = 18.dp))
        }
        // The caption counts finished days only, as the markers and the calendar draw them: today is
        // still in progress (a partial ring, not a verdict), so "5 of 6 days" sits under five checks and
        // one "!". With nothing finished yet it just names the target. The Averages card keeps the
        // period's own count, today included, as its averages do.
        val (good, finished) = finishedDayCounts(state.bars, LocalDate.now().toEpochDay())
        val target = nf.format(state.avgTargetKcal.toLong())
        val days = if (finished > 0) pluralStringResource(R.plurals.stats_caption_days, finished, good, finished) else null
        val rest = if (days != null) {
            stringResource(R.string.stats_caption_rest, target)
        } else {
            stringResource(R.string.dashboard_target) + " " + target + " " + stringResource(R.string.kcal_unit)
        }
        BasicText(
            buildAnnotatedString {
                if (days != null) {
                    withStyle(SpanStyle(color = c.label, fontWeight = FontWeight.SemiBold)) { append(days) }
                    append(" ")
                }
                append(rest)
            },
            Modifier
                .padding(top = 14.dp)
                .emberEntrance(1, EntranceKind.FadeUp, first, entrances, "average-caption"),
            style = t.subhead.copy(fontWeight = FontWeight.Normal, color = c.label2),
        )
    }
}

/**
 * On-plan days to logged days among the period's finished days ([today] left out): the numbers of
 * "5 of 6 days on plan".
 */
private fun finishedDayCounts(bars: List<DayBar>, today: Long): Pair<Int, Int> {
    val past = bars.filter { it.epochDay != today }
    return past.count { it.state == AdherenceCalculator.DayState.GOOD } to
        past.count { it.state != AdherenceCalculator.DayState.EMPTY }
}

/** The day's own name for TalkBack and the chart header: "Tuesday, October 6". */
private fun longDay(date: LocalDate, locale: Locale): String = date.format(bestPattern(locale, "EEEEdMMMM")).capitalized(locale)

/**
 * The week as seven markers, oldest first: an Ember disc with a check (on plan), an ink ring with
 * "!" (over), today's mini ring, a dashed ring (nothing logged). They pop one after another on the
 * first open. Each reads as "Tuesday, October 6: 2,060 kcal, on plan".
 */
@Composable
private fun WeekMarkers(state: StatsUiState, first: Boolean, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val nf = rememberIntegerFormat()
    val today = LocalDate.now().toEpochDay()
    val todayLabel = stringResource(R.string.tab_today)
    Row(modifier.fillMaxWidth()) {
        state.bars.forEachIndexed { i, bar ->
            val date = LocalDate.ofEpochDay(bar.epochDay)
            val isToday = bar.epochDay == today
            val kcal = nf.format(bar.eatenKcal.toLong())
            val name = longDay(date, locale)
            val (marker, description) = when {
                isToday -> MarkerState.Today to stringResource(R.string.marker_today, name, kcal)
                bar.state == AdherenceCalculator.DayState.GOOD -> MarkerState.Good to stringResource(R.string.marker_good, name, kcal)
                bar.state == AdherenceCalculator.DayState.OVER -> MarkerState.Over to stringResource(R.string.marker_over, name, kcal)
                else -> MarkerState.Empty to stringResource(R.string.marker_empty, name)
            }
            DayMarker(
                state = marker,
                contentDescription = description,
                // A little air between neighbours: at large text sizes the labels shrink to fit
                // rather than run into each other ("SunMonTue").
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                label = if (isToday) todayLabel else shortWeekday(date, locale),
                progress = if (state.avgTargetKcal > 0) (bar.eatenKcal / state.avgTargetKcal).toFloat() else 0f,
                popDelayMillis = if (first) 260 + 50 * i else null,
            )
        }
    }
}

/**
 * The empty period's picture (the web's): seven ghost capsules where the days' bars would stand, the
 * last one, today, in the Ember track. Decorative.
 */
@Composable
private fun EmptyDaysArt() {
    val c = Ember.colors
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(7) { i ->
            val today = i == 6
            Box(
                Modifier
                    .size(width = 14.dp, height = 64.dp)
                    .then(if (today) Modifier else Modifier.border(.5.dp, c.sepStrong, EmberShapes.capsule))
                    .background(if (today) c.emberTrack else c.fill2, EmberShapes.capsule),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Calories by day
// ------------------------------------------------------------------------------------------------

/**
 * Calories by day: the selection header ("Today so far 1,385 kcal · 855 under the 2,240 target"),
 * the chart (ghost goal capsules, Ember bars, the hatched over cap, today striped, the dashed goal
 * with its pill showing the average target) and its key. Tap or drag across the bars to pick a day.
 */
@Composable
private fun ChartCard(state: StatsUiState, range: StatsRange, first: Boolean, modifier: Modifier) {
    val days = chartDays(state, range)
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val today = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: days.lastIndex
    val shown = (selected ?: today).coerceIn(0, (days.size - 1).coerceAtLeast(0))
    EmberCard(modifier) {
        CardHead(
            stringResource(R.string.stats_chart_title),
            meta = stringResource(
                when (range) {
                    StatsRange.WEEK -> R.string.chart_last_7
                    StatsRange.MONTH -> R.string.chart_last_30
                    StatsRange.NINETY -> R.string.chart_last_90
                },
            ),
        )
        if (days.isNotEmpty()) ChartHeader(days[shown], state.avgTargetKcal)
        CaloriesChart(
            days = days,
            target = state.avgTargetKcal,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(top = 8.dp),
            height = if (range == StatsRange.WEEK) 196.dp else 170.dp,
            enter = first,
        )
        CaloriesChartLegend(Modifier.padding(top = 12.dp))
    }
}

/** The bars as chart days: short weekdays for a week, a date every 7 (or 30) days otherwise, "Today" in tint. */
@Composable
private fun chartDays(state: StatsUiState, range: StatsRange): List<ChartDay> {
    val locale = LocalConfiguration.current.locales[0]
    val todayLabel = stringResource(R.string.tab_today)
    val today = LocalDate.now().toEpochDay()
    return remember(state.bars, range, locale, todayLabel, today) {
        val dayMonth = bestPattern(locale, "dMMM")
        val longName = bestPattern(locale, "EEEEdMMM")
        val every = if (range == StatsRange.NINETY) 30 else 7
        val n = state.bars.size
        state.bars.mapIndexed { i, bar ->
            val date = LocalDate.ofEpochDay(bar.epochDay)
            val isToday = bar.epochDay == today
            val fromEnd = n - 1 - i
            ChartDay(
                label = when {
                    isToday -> todayLabel
                    range == StatsRange.WEEK -> shortWeekday(date, locale)
                    else -> date.format(dayMonth).trimEnd('.')
                },
                kcal = bar.eatenKcal.toInt(),
                isToday = isToday,
                labelShown = range == StatsRange.WEEK || isToday || (fromEnd % every == 0 && fromEnd >= every),
                longLabel = date.format(longName).capitalized(locale),
                over = bar.state == AdherenceCalculator.DayState.OVER,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Adherence calendar
// ------------------------------------------------------------------------------------------------

/**
 * The month as day markers with the day's number inside: on plan (Ember disc), over (ink ring),
 * today (mini ring, number in tint), not logged (dashed ring), future (grey number). ‹ › page the
 * months (› stops at the current one). Cells are not tappable. The discs pop in a diagonal wave on
 * the first open.
 */
@Composable
private fun CalendarCard(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    first: Boolean,
    modifier: Modifier,
) {
    val c = Ember.colors
    val t = Ember.type
    val locale = LocalConfiguration.current.locales[0]
    val month = state.calendarMonth
    // Read on every composition: after midnight the next refresh rolls the screen to the new day.
    val today = LocalDate.now()
    // Only the month first shown pops; paging to another month just shows it.
    val firstMonth = remember { month }
    val monthLabel = remember(month, locale) {
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).capitalized(locale) + " " + month.year
    }
    val nf = rememberIntegerFormat()
    val kcalByDay = remember(state.bars) { state.bars.associate { it.epochDay to it.eatenKcal } }

    EmberCard(modifier) {
        CardHead(stringResource(R.string.stats_calendar_title), icon = EmberIcons.Calendar)
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            EmberIconButton(
                EmberIcons.Left,
                stringResource(R.string.stats_prev_month),
                onPreviousMonth,
                Modifier.offset(x = (-10).dp),
                style = IconButtonStyle.Plain,
            )
            BasicText(
                monthLabel,
                Modifier.weight(1f),
                style = t.headline.copy(fontSize = 16.sp, color = c.label, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            EmberIconButton(
                EmberIcons.Right,
                stringResource(R.string.stats_next_month),
                onNextMonth,
                Modifier.offset(x = 10.dp),
                style = IconButtonStyle.Plain,
                enabled = month < YearMonth.now(),
            )
        }
        // Mon..Sun header, the locale's narrow names ("M", "П"...).
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            DayOfWeek.entries.forEach { dow ->
                BasicText(
                    dow.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    Modifier.weight(1f),
                    style = t.caption.copy(color = c.label2, textAlign = TextAlign.Center),
                )
            }
        }
        // Leading blanks align day 1 under its weekday (Monday-first).
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<Int?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).toList()
        val legendWords = mapOf(
            MarkerState.Good to stringResource(R.string.legend_on_plan),
            MarkerState.Over to stringResource(R.string.legend_over),
        )
        cells.chunked(7).forEachIndexed { row, week ->
            Row(Modifier.fillMaxWidth().padding(top = if (row > 0) 8.dp else 0.dp)) {
                week.forEachIndexed { col, dayOfMonth ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayOfMonth != null) {
                            val date = month.atDay(dayOfMonth)
                            val epochDay = date.toEpochDay()
                            val verdict = state.calendarStates[epochDay]
                            val marker = when {
                                date.isAfter(today) -> MarkerState.Future
                                date == today -> MarkerState.Today
                                verdict == AdherenceCalculator.DayState.GOOD -> MarkerState.Good
                                verdict == AdherenceCalculator.DayState.OVER -> MarkerState.Over
                                else -> MarkerState.Empty
                            }
                            val name = longDay(date, locale)
                            val kcal = kcalByDay[epochDay]
                            val description = when (marker) {
                                MarkerState.Future -> name
                                MarkerState.Empty -> stringResource(R.string.marker_empty, name)
                                MarkerState.Today -> stringResource(R.string.marker_today, name, nf.format((kcal ?: 0.0).toLong()))
                                MarkerState.Good -> kcal?.let { stringResource(R.string.marker_good, name, nf.format(it.toLong())) }
                                    ?: "$name: ${legendWords[marker]}"
                                MarkerState.Over -> kcal?.let { stringResource(R.string.marker_over, name, nf.format(it.toLong())) }
                                    ?: "$name: ${legendWords[marker]}"
                            }
                            DayMarker(
                                state = marker,
                                contentDescription = description,
                                dayNumber = nf.format(dayOfMonth.toLong()),
                                progress = if (state.avgTargetKcal > 0) ((kcal ?: 0.0) / state.avgTargetKcal).toFloat() else 0f,
                                popDelayMillis = if (first && month == firstMonth) 420 + 18 * (row + col) else null,
                            )
                        }
                    }
                }
                // Pad the last week so its cells keep the same width.
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        MarkerLegend(Modifier.padding(top = 14.dp))
    }
}

// ------------------------------------------------------------------------------------------------
// Averages
// ------------------------------------------------------------------------------------------------

/** Averages over the days that have entries (honest numbers), each with its metric's dot. One TalkBack node. */
@Composable
private fun AveragesCard(state: StatsUiState, modifier: Modifier) {
    val targets = state.targets ?: return
    val c = Ember.colors
    val t = Ember.type
    val value = t.rowNumber.copy(fontSize = 17.sp)
    val small = 13.sp
    EmberCard(modifier, mergeDescendants = true) {
        CardHead(stringResource(R.string.stats_summary_title))
        AverageRow(stringResource(R.string.stats_avg_kcal), c.ember2, divided = false) {
            NumberWithUnit(state.avgEatenKcal.toLong(), stringResource(R.string.kcal_unit), value, unitSize = small)
        }
        AverageRow(stringResource(R.string.summary_protein), c.protein) {
            ValueText(stringResource(R.string.macro_pair, state.avgProteinG, targets.proteinG), value, unitSize = small, unitWeight = FontWeight.Medium, firstNumberOnly = true)
        }
        AverageRow(stringResource(R.string.summary_fat), c.fat) {
            ValueText(stringResource(R.string.macro_pair, state.avgFatG, targets.fatG), value, unitSize = small, unitWeight = FontWeight.Medium, firstNumberOnly = true)
        }
        AverageRow(stringResource(R.string.summary_carbs), c.carbs) {
            ValueText(stringResource(R.string.macro_pair, state.avgCarbsG, targets.carbsG), value, unitSize = small, unitWeight = FontWeight.Medium, firstNumberOnly = true)
        }
        AverageRow(stringResource(R.string.water_label), c.water) {
            NumberWithUnit(state.avgWaterMl.toLong(), stringResource(R.string.ml_unit), value, unitSize = small)
        }
        AverageRow(stringResource(R.string.stats_days_on_plan), null) {
            ValueText(stringResource(R.string.stats_days_value, state.goodDays, state.loggedDays), value, unitSize = small, unitWeight = FontWeight.Medium, firstNumberOnly = true)
        }
    }
}

@Composable
private fun AverageRow(label: String, dot: Color?, divided: Boolean = true, value: @Composable () -> Unit) {
    val c = Ember.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divided) Modifier.topHairline(c.sep, 0.dp) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Days on plan has no metric: the empty dot keeps its words in line with the others.
            Box(Modifier.size(8.dp).background(dot ?: Color.Transparent, EmberShapes.circle))
            BasicText(label, style = Ember.type.subhead.copy(fontWeight = FontWeight.Normal, color = c.label))
        }
        value()
    }
}

// ------------------------------------------------------------------------------------------------
// Diet analysis
// ------------------------------------------------------------------------------------------------

/** Which nutrient the top-sources list currently shows. */
private enum class SourceTab { PROTEIN, FAT, CARBS, SUGARS }

/**
 * "Diet analysis": the gaps and excesses of the period first (the "so what" of the card), each in
 * its own block with a warning glyph, or "No gaps or excesses" with a check; then the top-5 foods
 * per nutrient, each with a thin bar against the first.
 */
@Composable
private fun AnalysisCard(state: StatsUiState, modifier: Modifier) {
    val sources = state.topSources ?: return
    val c = Ember.colors
    val t = Ember.type
    var tab by rememberSaveable { mutableStateOf(SourceTab.PROTEIN) }

    EmberCard(modifier) {
        CardHead(stringResource(R.string.stats_analysis_title), icon = EmberIcons.Sparkle)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.insights.isEmpty()) {
                InsightBlock(stringResource(R.string.insight_all_good), null, EmberIcons.Check, c.good)
            } else {
                state.insights.forEach { insight -> InsightBlock(insightText(insight), insight.avg, EmberIcons.Warning, c.fatInk) }
            }
        }
        BasicText(
            stringResource(R.string.stats_top_sources_title),
            Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
            style = t.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2),
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.summary_protein),
                stringResource(R.string.summary_fat),
                stringResource(R.string.summary_carbs),
                stringResource(R.string.nutrient_sugars),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { tab = SourceTab.entries[it] },
            role = SegmentRole.Radio,
        )
        val (list, color) = when (tab) {
            SourceTab.PROTEIN -> sources.protein to c.protein
            SourceTab.FAT -> sources.fat to c.fat
            SourceTab.CARBS -> sources.carbs to c.carbs
            // Sugars share the amber of the sugars limit on Today.
            SourceTab.SUGARS -> sources.sugars to c.fat
        }
        if (list.isEmpty()) {
            BasicText(
                stringResource(R.string.stats_no_sources),
                Modifier.padding(start = 2.dp, top = 12.dp, bottom = 4.dp),
                style = t.footnote.copy(fontSize = 15.sp, color = c.label2),
            )
        } else {
            Column(Modifier.padding(top = 6.dp)) {
                val top = list.first().amount.takeIf { it > 0.0 } ?: 1.0
                list.forEachIndexed { index, item -> SourceRow(index + 1, item, (item.amount / top).toFloat(), color, divided = index > 0) }
            }
        }
    }
}

/** One gap or excess on the quieter nested surface; its average value in bold so the number stands out. */
@Composable
private fun InsightBlock(text: String, value: Double?, icon: EmberIcons, glyph: Color) {
    val c = Ember.colors
    val locale = LocalConfiguration.current.locales[0]
    val annotated = remember(text, value, locale) {
        // A number keeps the word after it on its line ("31 g", "19.0 of"): no unit left alone.
        val kept = text.replace(Regex("(?<=\\d) "), "\u00A0")
        val shown = value?.let { String.format(locale, "%.1f", it) }
        val at = shown?.let { kept.indexOf(it) } ?: -1
        buildAnnotatedString {
            if (at < 0 || shown == null) {
                append(kept)
            } else {
                append(kept.substring(0, at))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.label)) { append(shown) }
                append(kept.substring(at + shown.length))
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, EmberShapes.nestedSmall)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmberIcon(icon, null, size = 20.dp, tint = glyph)
        BasicText(annotated, style = Ember.type.subhead.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Normal, color = c.label))
    }
}

/** "1  Chicken breast  412.0 g" with a 4 dp bar under the name, as long as its share of the first. */
@Composable
private fun SourceRow(rank: Int, item: FoodContribution, fraction: Float, color: Color, divided: Boolean) {
    val c = Ember.colors
    val t = Ember.type
    val nf = rememberIntegerFormat()
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (divided) Modifier.topHairline(c.sep, 30.dp) else Modifier)
            .padding(vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(nf.format(rank.toLong()), Modifier.width(22.dp), style = t.rowNumber.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.label2))
            BasicText(item.name, Modifier.weight(1f), style = t.subhead.copy(fontWeight = FontWeight.Normal, color = c.label), maxLines = 1)
            ValueText(stringResource(R.string.nutrient_grams_value, item.amount), t.rowNumber.copy(fontSize = 15.sp), unitSize = 12.sp)
        }
        Bar(fraction, color, contentDescription = null, modifier = Modifier.padding(start = 30.dp, top = 6.dp), height = 4.dp)
    }
}

@Composable
private fun insightText(insight: DietInsights.Insight): String = stringResource(
    when (insight.kind) {
        DietInsights.Kind.PROTEIN_LOW -> R.string.insight_protein_low
        DietInsights.Kind.FIBER_LOW -> R.string.insight_fiber_low
        DietInsights.Kind.SUGAR_HIGH -> R.string.insight_sugar_high
        DietInsights.Kind.SALT_HIGH -> R.string.insight_salt_high
        DietInsights.Kind.SAT_FAT_HIGH -> R.string.insight_sat_fat_high
    },
    insight.avg,
    insight.reference,
)

// ------------------------------------------------------------------------------------------------
// Loading
// ------------------------------------------------------------------------------------------------

/**
 * Loading: the average's two lines and seven 36 dp circles where the markers go, then the chart and
 * calendar card shapes, all pulsing gently. TalkBack hears "Loading…" once.
 */
@Composable
private fun StatsSkeleton() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
            SkeletonLine(120.dp, height = 14.dp)
            Spacer(Modifier.height(12.dp))
            SkeletonLine(210.dp, height = 58.dp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                repeat(7) { Skeleton(Modifier.size(36.dp), EmberShapes.circle) }
            }
            Spacer(Modifier.height(16.dp))
            SkeletonLine(240.dp, height = 14.dp)
        }
        Skeleton(Modifier.fillMaxWidth().height(340.dp))
        Skeleton(Modifier.fillMaxWidth().height(360.dp))
    }
}
