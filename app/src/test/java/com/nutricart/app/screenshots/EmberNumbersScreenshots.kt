package com.nutricart.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.ember.Bar
import com.nutricart.app.ui.ember.CaloriesChart
import com.nutricart.app.ui.ember.CaloriesChartLegend
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ChartDay
import com.nutricart.app.ui.ember.ChartHeader
import com.nutricart.app.ui.ember.CompositionBar
import com.nutricart.app.ui.ember.DayMarker
import com.nutricart.app.ui.ember.Digits
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSpinner
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.MarkerLegend
import com.nutricart.app.ui.ember.MarkerState
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSize
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRing
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.WeightLineChart
import com.nutricart.app.ui.ember.rememberDecimalFormat
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberHeroRingSize
import com.nutricart.app.ui.ember.ringEntrance
import com.nutricart.app.ui.ember.ringHalo
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wave 1, B1 Numbers: the ring at every size and fill, digits arriving and handing off, bars,
 * day markers in every state, the calories chart (week, month, 90 days, a picked day), the
 * weight chart, skeletons and spinners, plus their motion as frames. The harness world: Saturday
 * 10 October 2026, target 2,240 kcal, 1,385 eaten.
 */
class EmberNumbersScreenshots(variant: Variant) : ScreenshotTest(variant) {

    // -------------------------------------------------------------------------------- page frame

    @Composable
    private fun Page(scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
        val base = Modifier.fillMaxSize().background(Ember.colors.bg)
        Column(
            (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    @Composable
    private fun Caption(text: String) =
        BasicText(text, style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, color = Ember.colors.label2))

    @Composable
    private fun Small(text: String) = BasicText(text, style = Ember.type.footnote.copy(color = Ember.colors.label2))

    // -------------------------------------------------------------------------------- data

    private val target = 2240
    private val eaten = 1385

    /** Sunday 4 to Saturday 10 October: on plan, within 5%, on plan, over, on plan, within, today. */
    private val week = listOf(2150, 2310, 2080, 2440, 2190, 2260, 1385)

    @Composable
    private fun weekDays(): List<ChartDay> {
        val locale = LocalConfiguration.current.locales[0]
        val today = stringResource(R.string.tab_today)
        val short = DateTimeFormatter.ofPattern("EEE", locale)
        val long = DateTimeFormatter.ofPattern("EEEE d MMM", locale)
        return week.mapIndexed { i, kcal ->
            val day = LocalDate.of(2026, 10, 4).plusDays(i.toLong())
            ChartDay(
                label = if (i == 6) today else day.format(short).trimEnd('.').replaceFirstChar { it.uppercase() },
                kcal = kcal,
                isToday = i == 6,
                longLabel = day.format(long).replaceFirstChar { it.uppercase() },
            )
        }
    }

    /** A month (or 90 days) ending today: mostly on plan, a few over, two empty days. */
    @Composable
    private fun rangeDays(count: Int, every: Int): List<ChartDay> {
        val locale = LocalConfiguration.current.locales[0]
        val today = stringResource(R.string.tab_today)
        val fmt = DateTimeFormatter.ofPattern("d MMM", locale)
        val first = LocalDate.of(2026, 10, 10).minusDays(count - 1L)
        return (0 until count).map { i ->
            val day = first.plusDays(i.toLong())
            val isToday = i == count - 1
            val wave = ((i * 37) % 23) - 11
            val kcal = when {
                isToday -> eaten
                i % 13 == 5 -> 0
                i % 9 == 4 -> 2440 + wave * 6
                else -> 2120 + wave * 14
            }
            ChartDay(
                label = if (isToday) today else day.format(fmt).trimEnd('.'),
                kcal = kcal,
                isToday = isToday,
                labelShown = isToday || (count - 1 - i) % every == 0 && count - 1 - i >= every,
            )
        }
    }

    /** One weighing every two or three days over the last month, drifting down from 79.8 to 78.4. */
    private val weights: List<Pair<Long, Double>> = run {
        val today = LocalDate.of(2026, 10, 10).toEpochDay()
        val days = listOf(30, 28, 25, 23, 20, 18, 15, 13, 10, 8, 6, 3, 1, 0)
        val kg = listOf(79.8, 79.9, 79.6, 79.4, 79.5, 79.2, 79.0, 79.1, 78.8, 78.9, 78.7, 78.6, 78.5, 78.4)
        days.zip(kg).map { (ago, w) -> today - ago to w }
    }

    // -------------------------------------------------------------------------------- rings

    /** The Today hero: ring, remaining digits, caption; [first] plays the whole first-open build. */
    @Composable
    private fun Hero(value: Int, first: Boolean, from: Int? = null, zone: Boolean = false, shineAt: Int? = null) {
        val size = rememberHeroRingSize()
        val left = target - value
        val over = left < 0
        Box(Modifier.fillMaxWidth().ringHalo(bloom = first), contentAlignment = Alignment.Center) {
            Ring(
                value = value.toFloat(),
                target = target.toFloat(),
                size = size.size,
                stroke = size.stroke,
                modifier = Modifier.ringEntrance(first),
                from = from?.toFloat(),
                sweep = first || from != null,
                delayMillis = if (from != null) 160 else 140,
                shineAtMillis = shineAt,
                zone = zone,
                contentDescription = "$value of $target kcal eaten",
            ) {
                Column(Modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FittedNumber(
                        value = abs(left).toLong(),
                        numberStyle = Ember.type.heroNumeral,
                        enter = first,
                        delayMillis = 200,
                        from = from?.let { abs(target - it).toLong() },
                        color = Ember.colors.label,
                    )
                    BasicText(
                        stringResource(if (over) R.string.day_kcal_over else R.string.day_kcal_left),
                        style = Ember.type.subhead.copy(color = Ember.colors.label2, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }

    @Composable
    private fun SizedRing(size: RingSize, value: Float, add: Float? = null, muted: Boolean = false, centre: String? = null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Ring(value, target.toFloat(), size.size, size.stroke, add = add, muted = muted) {
                if (centre != null) BasicText(centre, style = Ember.type.rowNumber.copy(color = Ember.colors.label))
            }
            Small("${size.size.value.roundToInt()}/${size.stroke.value.roundToInt()}")
        }
    }

    @Test
    @KeyScreen
    fun ringHero() = shoot("numbers-ring-hero") {
        Page {
            Caption("Hero 252/26 · 1,385 of 2,240 · halo")
            Hero(eaten, first = false)
            Caption("Over target: 2,530 of 2,240 (the ink second lap)")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(2530f, target.toFloat(), RingSizes.Plan.size, RingSizes.Plan.stroke) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Digits(290, Ember.type.stat)
                        Small(stringResource(R.string.day_kcal_over))
                    }
                }
                Ring(1385f, target.toFloat(), RingSizes.Sheet.size, RingSizes.Sheet.stroke, add = 161f, muted = true) {
                    BasicText("69%", style = Ember.type.headline.copy(color = Ember.colors.label))
                }
            }
        }
    }

    @Test
    @KeyScreen
    fun ringSizes() = shoot("numbers-ring-sizes") {
        Page {
            Caption("Every RingSizes entry at 62%")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                SizedRing(RingSizes.Plan, 1385f)
                SizedRing(RingSizes.Onboarding, 1860f)
                SizedRing(RingSizes.Sheet, 1385f, add = 161f, muted = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                SizedRing(RingSizes.Summary, 1385f)
                SizedRing(RingSizes.DayCard, 1385f)
                SizedRing(RingSizes.Marker, 1385f)
                SizedRing(RingSizes.Mini, 1385f)
            }
            Caption("0 · 62% · 98% · 113% · 62% + this food (muted)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f, 1385f, 2195f, 2530f).forEach { v ->
                    Ring(v, target.toFloat(), 64.dp, 8.dp)
                }
                Ring(1385f, target.toFloat(), 64.dp, 8.dp, add = 420f, muted = true)
            }
            Caption("Near closure: 92% · 97% · 100% · 150% · 200%")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2060f, 2175f, 2240f, 3360f, 4480f).forEach { v ->
                    Ring(v, target.toFloat(), 64.dp, 8.dp)
                }
            }
        }
    }

    /** T1–T5: the first open of Today: the ring turns in, sweeps, the digits arrive, the glow blooms, one light pass. */
    @Test
    fun ringFirstOpenFrames() = shootFrames("numbers-ring-first", times = listOf(60, 250, 600, 900, 1250, 2200)) {
        Page { Hero(eaten, first = rememberFirstOpen("numbers"), shineAt = 700) }
    }

    /** Z1/Z2: in the zone (2,080 of 2,240): two halo pulses and a light pass after the sweep. */
    @Test
    fun ringZoneFrames() = shootFrames("numbers-ring-zone", times = listOf(1300, 1550, 1800, 2400, 2900, 3600)) {
        Page { Hero(2080, first = rememberFirstOpen("numbers"), zone = true, shineAt = 1250) }
    }

    /** A2/A3: back from Add (161 kcal): the ring re-sweeps from 1,385 and the remaining digits hand off 855 → 694. */
    @Test
    @KeyScreen
    fun ringBackFromAddFrames() = shootFrames(
        "numbers-ring-back-from-add", times = listOf(100, 200, 280, 400, 700, 1400), firstOpen = false,
    ) {
        Page { Hero(1546, first = false, from = eaten) }
    }

    // -------------------------------------------------------------------------------- digits

    @Test
    @KeyScreen
    @Tall(1150)
    fun digits() = shoot("numbers-digits") {
        Page {
            val c = Ember.colors
            Caption("heroNumeral · weekHero gradient (20 sp unit) · display gradient (17 sp unit)")
            Digits(855, Ember.type.heroNumeral)
            NumberWithUnit(2238, stringResource(R.string.kcal_unit), Ember.type.weekHero, gradient = true, unitSize = 20.sp)
            NumberWithUnit(318, stringResource(R.string.kcal_unit), Ember.type.display, gradient = true, unitSize = 17.sp)
            Caption("stat · statSmall · kpi · rowNumber, with units")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.Bottom) {
                NumberWithUnit(1385, stringResource(R.string.kcal_unit), Ember.type.stat)
                NumberWithUnit(72, "g", Ember.type.statSmall)
                NumberWithUnit(2510, stringResource(R.string.kcal_unit), Ember.type.kpi)
                NumberWithUnit(525, stringResource(R.string.kcal_unit), Ember.type.rowNumber)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.Bottom) {
                NumberWithUnit(78.4, "kg", Ember.type.stat)
                NumberWithUnit(9.7, "g", Ember.type.statSmall, format = rememberDecimalFormat(1))
                Digits(12345678, Ember.type.kpi, color = c.tint)
                Digits(-0.4, Ember.type.kpi)
            }
            Caption("Fitted: 12,345 kcal in 150 dp, then 855 (fits)")
            Box(Modifier.width(150.dp).background(c.fill2, EmberShapes.field)) {
                FittedNumber(12345, Ember.type.heroNumeral, unit = stringResource(R.string.kcal_unit), minFontSize = 24.sp)
            }
            Box(Modifier.width(150.dp).background(c.fill2, EmberShapes.field)) {
                FittedNumber(855, Ember.type.heroNumeral)
            }
            Caption("Next to text on one baseline")
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("Eaten ", Modifier.alignByBaseline(), style = Ember.type.body.copy(color = c.label2))
                NumberWithUnit(1385, stringResource(R.string.kcal_unit), Ember.type.kpi, Modifier.alignByBaseline())
            }
        }
    }

    @Composable
    private fun HandoffRows() {
        val kcal = stringResource(R.string.kcal_unit)
        Page {
            Caption("870 → 709 (down)")
            NumberWithUnit(709, kcal, Ember.type.heroNumeral, from = 870)
            Caption("1,230 → 1,391 (up)")
            NumberWithUnit(1391, kcal, Ember.type.stat, from = 1230)
            Caption("999 → 1,000 (a new shape arrives again)")
            NumberWithUnit(1000, kcal, Ember.type.stat, from = 999)
            Caption("Gradient 2,238 → 2,105")
            NumberWithUnit(2105, kcal, Ember.type.weekHero, gradient = true, from = 2238, unitSize = 20.sp)
            Caption("78.4 → 78.1 kg")
            NumberWithUnit(78.1, "kg", Ember.type.stat, from = 78.4)
        }
    }

    /** §3.4 D: digit by digit, never an intermediate value or two digits in one cell. */
    @Test
    @KeyScreen
    fun digitsHandoffFrames() = shootFrames(
        "numbers-digits-handoff", times = listOf(40, 100, 125, 135, 170, 240, 360, 700), firstOpen = false,
    ) { HandoffRows() }

    /** T3/K1: the first open, each digit rises in turn (200 ms, 60 apart); separators fade with their digit. */
    @Test
    fun digitsEnterFrames() = shootFrames("numbers-digits-enter", times = listOf(150, 260, 330, 400, 520, 900)) {
        val first = rememberFirstOpen("numbers")
        Page {
            Digits(855, Ember.type.heroNumeral, enter = first, delayMillis = 200)
            NumberWithUnit(2238, stringResource(R.string.kcal_unit), Ember.type.weekHero, gradient = true, enter = first, delayMillis = 170, unitSize = 20.sp)
            NumberWithUnit(1385, stringResource(R.string.kcal_unit), Ember.type.kpi, enter = first, delayMillis = 260)
        }
    }

    private val live = mutableLongStateOf(870L)

    /** A change while shown (a water tap, a delete): the hand-off starts on the next frame. */
    @Test
    fun digitsLiveChangeFrames() = shootFrames(
        "numbers-digits-live", times = listOf(16, 80, 130, 200, 320, 700), firstOpen = false,
        interact = { runOnUiThread { live.longValue = 709L } },
    ) {
        Page {
            Caption("870 → 709, changed while on screen")
            Digits(live.longValue, Ember.type.heroNumeral)
            NumberWithUnit(live.longValue + 1000, stringResource(R.string.kcal_unit), Ember.type.stat, gradient = true)
        }
    }

    // -------------------------------------------------------------------------------- bars

    @Composable
    private fun MacroColumn(name: String, value: Int, of: Int, metric: Metric, modifier: Modifier, delay: Int?) {
        val c = Ember.colors
        val color = when (metric) {
            Metric.Protein -> c.protein
            Metric.Fat -> c.fat
            else -> c.carbs
        }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(color, EmberShapes.circle))
                Small(name)
            }
            NumberWithUnit(value.toLong(), "g", Ember.type.statSmall)
            Bar(value / of.toFloat(), color, "$name $value of $of g", fillDelayMillis = delay)
            Small(stringResource(R.string.of_target_g, of))
        }
    }

    @Composable
    private fun BarsPage(first: Boolean) {
        val c = Ember.colors
        Page {
            EmberCard {
                CardHead(stringResource(R.string.macros_title), meta = stringResource(R.string.macros_meta))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MacroColumn("Protein", 72, 140, Metric.Protein, Modifier.weight(1f), if (first) 420 else null)
                    MacroColumn("Fat", 49, 72, Metric.Fat, Modifier.weight(1f), if (first) 500 else null)
                    MacroColumn("Carbs", 168, 252, Metric.Carbs, Modifier.weight(1f), if (first) 580 else null)
                }
                Spacer(Modifier.height(14.dp))
                listOf(
                    Triple("Fiber", 19.0f to 31f, c.carbs),
                    Triple("Sugars", 41.5f to 50f, c.fat),
                    Triple("Salt", 6.2f to 5f, c.weight),
                    Triple("Saturated fat", 12.4f to 22f, c.protein),
                ).forEachIndexed { i, (name, v, color) ->
                    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicText(name, Modifier.weight(1f), style = Ember.type.callout.copy(color = c.label))
                            BasicText("${v.first} / ${v.second.roundToInt()} g", style = Ember.type.rowNumber.copy(color = c.label))
                        }
                        Bar(v.first / v.second, color, "$name ${v.first} of ${v.second} g", height = 4.dp, fillDelayMillis = if (first) 660 + 80 * i else null)
                    }
                }
            }
            EmberCard {
                CardHead("Composition", icon = EmberIcons.Flame, metric = Metric.Kcal)
                Spacer(Modifier.height(12.dp))
                CompositionBar(288f, 441f, 672f, growDelayMillis = if (first) 220 else null)
                Spacer(Modifier.height(12.dp))
                CompositionBar(560f, 0f, 400f, growDelayMillis = if (first) 300 else null)
                Spacer(Modifier.height(12.dp))
                CompositionBar(0f, 0f, 0f)
            }
            EmberCard {
                Small(stringResource(R.string.shopping_progress, 3, 10))
                Spacer(Modifier.height(8.dp))
                Bar(.3f, c.ember2, null, brush = EmberBrushes.emberIcon(c), fillDelayMillis = if (first) 300 else null)
                Spacer(Modifier.height(10.dp))
                Bar(0f, c.water, null)
                Spacer(Modifier.height(10.dp))
                Bar(1f, c.water, null)
            }
        }
    }

    @Test
    @KeyScreen
    fun bars() = shoot("numbers-bars") { BarsPage(first = false) }

    /** T7 and S9: bars fill from their left end (420 + 80 ms apart), the composition grows (220, 70 apart). */
    @Test
    fun barsFillFrames() = shootFrames("numbers-bars-fill", times = listOf(300, 520, 700, 1000, 1600)) {
        BarsPage(first = rememberFirstOpen("numbers"))
    }

    // -------------------------------------------------------------------------------- markers

    @Composable
    private fun WeekMarkers(first: Boolean) {
        val days = weekDays()
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val state = when {
                    d.isToday -> MarkerState.Today
                    d.kcal > target * 1.05 -> MarkerState.Over
                    else -> MarkerState.Good
                }
                DayMarker(
                    state = state,
                    contentDescription = "${d.longLabel}: ${d.kcal} kcal",
                    modifier = Modifier.weight(1f),
                    label = d.label,
                    progress = d.kcal / target.toFloat(),
                    popDelayMillis = if (first) 260 + 50 * i else null,
                )
            }
        }
    }

    @Composable
    private fun Calendar(first: Boolean) {
        val c = Ember.colors
        val locale = LocalConfiguration.current.locales[0]
        EmberCard {
            CardHead(stringResource(R.string.stats_calendar_title), icon = EmberIcons.Calendar)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach {
                    BasicText(
                        it.getDisplayName(DateTextStyle.NARROW, locale),
                        Modifier.weight(1f),
                        style = Ember.type.caption.copy(color = c.label2, textAlign = TextAlign.Center),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // October 2026 starts on a Thursday (Monday first): three blank cells.
            val cells = List(3) { 0 } + (1..31).toList()
            cells.chunked(7).forEachIndexed { row, week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    week.forEachIndexed { col, day ->
                        if (day == 0) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val state = when {
                                day == 10 -> MarkerState.Today
                                day > 10 -> MarkerState.Future
                                day == 8 -> MarkerState.Empty
                                day == 9 -> MarkerState.Over
                                else -> MarkerState.Good
                            }
                            DayMarker(
                                state = state,
                                contentDescription = "October $day",
                                modifier = Modifier.weight(1f),
                                dayNumber = day.toString(),
                                progress = .62f,
                                popDelayMillis = if (first) 200 + 18 * (row + col) else null,
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            MarkerLegend()
        }
    }

    @Composable
    private fun MarkersPage(first: Boolean) {
        Page {
            Caption("Week: on plan · over · today's mini ring")
            WeekMarkers(first)
            Caption("Good · Over · Today 113% · Empty · Future")
            Row(Modifier.fillMaxWidth()) {
                DayMarker(MarkerState.Good, null, Modifier.weight(1f), label = "Good")
                DayMarker(MarkerState.Over, null, Modifier.weight(1f), label = "Over")
                DayMarker(MarkerState.Today, null, Modifier.weight(1f), label = "Today", progress = 1.13f)
                DayMarker(MarkerState.Empty, null, Modifier.weight(1f), label = "Empty")
                DayMarker(MarkerState.Future, null, Modifier.weight(1f), label = "Future", dayNumber = "24")
                DayMarker(MarkerState.Good, null, Modifier.weight(1f), label = "30 dp", size = 30.dp)
            }
            Calendar(first)
        }
    }

    @Test
    @KeyScreen
    fun markers() = shoot("numbers-markers") { MarkersPage(first = false) }

    /** K2 and the calendar wave: discs pop on the bouncy curve, today's mini ring sweeps after its pop. */
    @Test
    fun markersPopFrames() = shootFrames("numbers-markers-pop", times = listOf(250, 380, 520, 700, 1000, 1500)) {
        MarkersPage(first = rememberFirstOpen("numbers"))
    }

    // -------------------------------------------------------------------------------- charts

    @Composable
    private fun ChartCard(days: List<ChartDay>, meta: Int, picked: Int? = null, first: Boolean = false) {
        var selected by remember { mutableStateOf(picked) }
        val shown = days[selected ?: days.indexOfFirst { it.isToday }]
        EmberCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
            CardHead(stringResource(R.string.stats_chart_title), meta = stringResource(meta))
            Spacer(Modifier.height(8.dp))
            ChartHeader(shown, target)
            Spacer(Modifier.height(12.dp))
            CaloriesChart(days, target, selected, { selected = it }, enter = first)
            Spacer(Modifier.height(12.dp))
            CaloriesChartLegend()
        }
    }

    @Test
    @KeyScreen
    fun chartWeek() = shoot("numbers-chart-week") {
        Page { ChartCard(weekDays(), R.string.chart_last_7) }
    }

    /** K8: Wednesday picked (over): the header says so in bold, the other days dim. */
    @Test
    fun chartWeekPicked() = shoot("numbers-chart-week-picked") {
        Page {
            ChartCard(weekDays(), R.string.chart_last_7, picked = 3)
            ChartCard(weekDays(), R.string.chart_last_7, picked = 1)
        }
    }

    @Test
    @KeyScreen
    @Tall(1500)
    fun chartMonthAnd90() = shoot("numbers-chart-month-90") {
        Page {
            ChartCard(rangeDays(30, 7), R.string.chart_last_30)
            ChartCard(rangeDays(90, 30), R.string.chart_last_90)
        }
    }

    /** K3–K5: bars rise out of the axis, the goal line wipes in, the pill pops. */
    @Test
    fun chartEnterFrames() = shootFrames("numbers-chart-enter", times = listOf(300, 550, 800, 1100, 1400, 1900)) {
        Page { ChartCard(weekDays(), R.string.chart_last_7, first = rememberFirstOpen("numbers")) }
    }

    /** K8: tapping Wednesday dims the others and hands the header digits off (1,385 → 2,440). */
    @Test
    fun chartPickFrames() = shootFrames(
        "numbers-chart-pick", times = listOf(16, 120, 260, 600), firstOpen = false,
        interact = {
            onNode(hasContentDescription(str(R.string.stats_chart_title), substring = true))
                .performTouchInput { click(Offset((width - 44.dp.toPx()) * 3.5f / 7f, height / 2f)) }
        },
    ) {
        Page { ChartCard(weekDays(), R.string.chart_last_7) }
    }

    // -------------------------------------------------------------------------------- weight

    @Composable
    private fun WeightCard(first: Boolean) {
        val c = Ember.colors
        EmberCard {
            CardHead(stringResource(R.string.weight_card_title), icon = EmberIcons.Scale, metric = Metric.Weight, meta = stringResource(R.string.chart_last_30))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberWithUnit(78.4, "kg", Ember.type.stat)
                Box(Modifier.background(c.goodSoft, EmberShapes.capsule).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    BasicText("↓ 0.4 kg/week", style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.good))
                }
            }
            Spacer(Modifier.height(8.dp))
            WeightLineChart(weights, enter = first)
        }
    }

    @Test
    @KeyScreen
    fun weightChart() = shoot("numbers-weight") {
        Page {
            WeightCard(first = false)
            Caption("Two weighings · a flat month")
            EmberCard { WeightLineChart(weights.takeLast(2)) }
            EmberCard { WeightLineChart(weights.map { it.first to 80.0 }) }
        }
    }

    /** K6/K7: the line draws itself, the area fades up, the last point pops and pings once. */
    @Test
    fun weightDrawFrames() = shootFrames("numbers-weight-draw", times = listOf(500, 900, 1300, 1700, 2100, 2900)) {
        Page { WeightCard(first = rememberFirstOpen("numbers")) }
    }

    // -------------------------------------------------------------------------------- skeletons

    @Test
    @KeyScreen
    fun skeletons() = shoot("numbers-skeletons") {
        Page {
            val hero = rememberHeroRingSize()
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonRing(hero.size, hero.stroke)
                SkeletonLine(120.dp)
                SkeletonLine(200.dp, height = 12.dp)
            }
            Skeleton(Modifier.fillMaxWidth().height(96.dp))
            EmberCard(padding = PaddingValues(0.dp)) { SkeletonRows(3) }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                EmberSpinner(18.dp)
                EmberSpinner()
                EmberSpinner(28.dp, contentDescription = stringResource(R.string.loading))
                EmberSpinner(44.dp)
                Skeleton(Modifier.size(36.dp), EmberShapes.circle)
            }
        }
    }
}
