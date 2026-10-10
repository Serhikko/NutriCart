package com.nutricart.app.ui.ember

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Ember design tokens: the website's tokens.css, one for one, in Kotlin. Screens read them through
// Ember.colors / Ember.type / Ember.motion (Theme.kt) and never write a hex value, a radius or a
// TextStyle of their own. The only departure from the web is the materials: Compose has no backdrop
// blur, so the floating chrome is drawn nearly opaque instead.

private fun rgba(argb: Long, alpha: Float) = Color(argb).copy(alpha = alpha)

// ---------------------------------------------------------------------------------------------
// Colours
// ---------------------------------------------------------------------------------------------

@Immutable
data class EmberColors(
    val isDark: Boolean,
    // surfaces
    val bg: Color,          // page stage
    val surface: Color,     // cards, list groups, tiles, fields on cards
    val surface2: Color,    // nested blocks inside cards (insights, quick-add tiles, pairing code)
    val surface3: Color,    // pressed rows, skeleton blocks
    val sheet: Color,       // sheets
    val behind: Color,      // round the receded page while a sheet is up
    val stageEdge: Color,   // hairline round the receded page (dark only)
    // text
    val label: Color, val label2: Color,
    val label3: Color,      // chevrons, decoration only, never text
    val label4: Color,      // disabled glyphs, grabber, empty-day dashes
    // lines and fills (translucent, adapt to any surface)
    val sep: Color, val sepStrong: Color,
    val fill: Color,        // segmented track, icon buttons, chips, active tab pill
    val fill2: Color,       // field wells, ghost goal capsules, off time pills
    val fill3: Color,       // pressed fills, switch track off
    val thumb: Color,       // segmented thumb
    val knob: Color,        // switch knob (white in both themes)
    // intent
    val ink: Color, val onInk: Color,   // primary buttons, selected chip, toast, zone badge
    val onAccent: Color,    // glyphs on meal tiles, avatars, Ember discs (white in both themes)
    val tint: Color,        // links, plain buttons, eyebrows, back links, "Add food" rows
    val focus: Color,       // focus ring (keyboard / switch access)
    val danger: Color, val good: Color, val goodSoft: Color,
    // Ember
    val ember1: Color, val ember2: Color, val ember3: Color,
    val emberText1: Color, val emberText2: Color,   // gradient numerals (>= 28 sp bold only)
    val emberTrack: Color, val emberGlow: Color, val emberInk: Color,
    val overLap: Color,     // past the goal: the ring's second lap, over caps
    // data accents (x = graphic, xInk = text-safe label colour)
    val protein: Color, val proteinInk: Color,
    val fat: Color, val fatInk: Color,
    val carbs: Color, val carbsInk: Color,
    val water: Color, val water2: Color, val waterInk: Color, val waterSoft: Color,
    val weight: Color, val weightInk: Color,
    val steps: Color, val stepsInk: Color,
    // materials: floating chrome only; opaque enough without blur
    val matBar: Color, val matTop: Color, val matEdge: Color, val matLine: Color,
    val scrim: Color,
)

val EmberLight = EmberColors(
    isDark = false,
    bg = Color(0xFFF5F5F7), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF5F5F7), surface3 = Color(0xFFE8E8ED),
    sheet = Color(0xFFFFFFFF), behind = Color(0xFF000000), stageEdge = Color.Transparent,
    label = Color(0xFF1D1D1F), label2 = Color(0xFF6E6E73), label3 = Color(0xFF8E8E93), label4 = Color(0xFFC7C7CC),
    sep = rgba(0xFF3C3C43, .14f), sepStrong = rgba(0xFF3C3C43, .29f),
    fill = rgba(0xFF767680, .12f), fill2 = rgba(0xFF767680, .08f), fill3 = rgba(0xFF767680, .20f),
    thumb = Color(0xFFFFFFFF), knob = Color(0xFFFFFFFF),
    ink = Color(0xFF1D1D1F), onInk = Color(0xFFFFFFFF), onAccent = Color(0xFFFFFFFF),
    tint = Color(0xFFC2410C), focus = Color(0xFF0071E3),
    danger = Color(0xFFD70015), good = Color(0xFF167534), goodSoft = rgba(0xFF34C759, .14f),
    ember1 = Color(0xFFFF9F0A), ember2 = Color(0xFFFF5E1F), ember3 = Color(0xFFF0306A),
    emberText1 = Color(0xFFE0560C), emberText2 = Color(0xFFCE2A64),
    emberTrack = rgba(0xFFFF5E1F, .13f), emberGlow = rgba(0xFFFF6E28, .14f), emberInk = Color(0xFFC2410C),
    overLap = Color(0xFF1D1D1F),
    protein = Color(0xFF5E5CE6), proteinInk = Color(0xFF4644C9),
    fat = Color(0xFFF2A100), fatInk = Color(0xFF8F5B00),
    carbs = Color(0xFF22A559), carbsInk = Color(0xFF157A3E),
    water = Color(0xFF0A84FF), water2 = Color(0xFF64D2FF), waterInk = Color(0xFF0062CC), waterSoft = rgba(0xFF0A84FF, .12f),
    weight = Color(0xFF0FA3B1), weightInk = Color(0xFF0B6F79),
    steps = Color(0xFF22A559), stepsInk = Color(0xFF157A3E),
    matBar = rgba(0xFFFCFCFD, .94f), matTop = rgba(0xFFF5F5F7, .94f),
    matEdge = rgba(0xFFFFFFFF, .95f), matLine = rgba(0xFF000000, .07f),
    scrim = rgba(0xFF000000, .24f),
)

val EmberDark = EmberColors(
    isDark = true,
    bg = Color(0xFF000000), surface = Color(0xFF1C1C1E), surface2 = Color(0xFF2C2C2E), surface3 = Color(0xFF3A3A3C),
    sheet = Color(0xFF1C1C1E), behind = Color(0xFF000000), stageEdge = rgba(0xFFFFFFFF, .16f),
    label = Color(0xFFF5F5F7), label2 = Color(0xFFA1A1A6), label3 = Color(0xFF8D8D93), label4 = Color(0xFF48484A),
    sep = rgba(0xFFFFFFFF, .11f), sepStrong = rgba(0xFFFFFFFF, .22f),
    fill = rgba(0xFF767680, .24f), fill2 = rgba(0xFF767680, .16f), fill3 = rgba(0xFF767680, .36f),
    thumb = Color(0xFF636366), knob = Color(0xFFFFFFFF),
    ink = Color(0xFFF5F5F7), onInk = Color(0xFF000000), onAccent = Color(0xFFFFFFFF),
    tint = Color(0xFFFF9F5A), focus = Color(0xFF4BA3FF),
    danger = Color(0xFFFF6961), good = Color(0xFF30D158), goodSoft = rgba(0xFF30D158, .16f),
    ember1 = Color(0xFFFFB340), ember2 = Color(0xFFFF6A3D), ember3 = Color(0xFFFF375F),
    emberText1 = Color(0xFFFFB340), emberText2 = Color(0xFFFF4F6E),
    emberTrack = rgba(0xFFFF6A3D, .20f), emberGlow = rgba(0xFFFF6028, .26f), emberInk = Color(0xFFFF9F5A),
    overLap = Color(0xFFF5F5F7),
    protein = Color(0xFF7D7AFF), proteinInk = Color(0xFFA5A3FF),
    fat = Color(0xFFFFCC40), fatInk = Color(0xFFFFD460),
    carbs = Color(0xFF32D74B), carbsInk = Color(0xFF5EE07A),
    water = Color(0xFF2E9BFF), water2 = Color(0xFF64D2FF), waterInk = Color(0xFF64B5FF), waterSoft = rgba(0xFF2E9BFF, .18f),
    weight = Color(0xFF40D3DF), weightInk = Color(0xFF6BDDE6),
    steps = Color(0xFF32D74B), stepsInk = Color(0xFF5EE07A),
    matBar = rgba(0xFF242426, .94f), matTop = rgba(0xFF000000, .90f),
    matEdge = rgba(0xFFFFFFFF, .12f), matLine = rgba(0xFFFFFFFF, .10f),
    scrim = rgba(0xFF000000, .55f),
)

/** One metric mapped onto a graphic colour and a text-safe ink (the web's .m-* helpers). */
enum class Metric { Kcal, Protein, Fat, Carbs, Water, Weight, Steps }

fun EmberColors.of(m: Metric): Color = when (m) {
    Metric.Kcal -> ember2; Metric.Protein -> protein; Metric.Fat -> fat; Metric.Carbs -> carbs
    Metric.Water -> water; Metric.Weight -> weight; Metric.Steps -> steps
}

fun EmberColors.inkOf(m: Metric): Color = when (m) {
    Metric.Kcal -> emberInk; Metric.Protein -> proteinInk; Metric.Fat -> fatInk; Metric.Carbs -> carbsInk
    Metric.Water -> waterInk; Metric.Weight -> weightInk; Metric.Steps -> stepsInk
}

// ---------------------------------------------------------------------------------------------
// Brushes
// ---------------------------------------------------------------------------------------------

/**
 * A CSS-style `linear-gradient(<angle>deg, ...)` that fits whatever it paints: the gradient line runs
 * through the centre at [angleDeg] (0 = up, 90 = right) and is just long enough for the corners to
 * reach the first and last stop, exactly as a browser draws it.
 */
@Immutable
class AngledGradient(
    private val stops: Array<Pair<Float, Color>>,
    private val angleDeg: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val a = angleDeg * PI.toFloat() / 180f
        val dx = sin(a)
        val dy = -cos(a)
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = Offset(c.x - dx * half, c.y - dy * half),
            to = Offset(c.x + dx * half, c.y + dy * half),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }

    override fun equals(other: Any?) =
        other is AngledGradient && other.angleDeg == angleDeg && other.stops.contentEquals(stops)

    override fun hashCode() = 31 * stops.contentHashCode() + angleDeg.hashCode()
}

object EmberBrushes {
    /** 135°: amber, vermilion, raspberry. Active tab icon, + icon, toast check, streak flame, Ember discs. */
    fun emberIcon(c: EmberColors): Brush =
        AngledGradient(arrayOf(0f to c.ember1, .55f to c.ember2, 1f to c.ember3), 135f)

    /** Chart bars: amber at the top, vermilion at the base (web `#g-ember-v`). */
    fun emberBars(c: EmberColors): Brush = Brush.verticalGradient(0f to c.ember1, 1f to c.ember2)

    /**
     * Gradient numerals. Each digit cell is clipped separately, so the gradient is placed in row
     * coordinates: [width] is the whole number's width, [offsetX] this cell's x inside it.
     */
    fun emberText(c: EmberColors, width: Float, offsetX: Float): Brush = Brush.linearGradient(
        colors = listOf(c.emberText1, c.emberText2),
        start = Offset(-offsetX, 0f),
        end = Offset(width - offsetX, 0f),
    )

    /** The ring's right half and start cap: amber at the top to vermilion at 6 o'clock. */
    fun ringA(c: EmberColors, sizePx: Float, strokePx: Float): Brush = Brush.verticalGradient(
        0f to c.ember1, 1f to c.ember2, startY = strokePx, endY = sizePx - strokePx,
    )

    /** The ring's left half: raspberry at the top to vermilion at 6 o'clock (flat across the seam). */
    fun ringB(c: EmberColors, sizePx: Float, strokePx: Float): Brush = Brush.verticalGradient(
        0f to c.ember3, 1f to c.ember2, startY = strokePx, endY = sizePx - strokePx,
    )

    /** Water glasses: light blue at the top, water blue at the bottom. */
    fun water(c: EmberColors): Brush = Brush.verticalGradient(listOf(c.water2, c.water))

    /** The weight chart's line, water blue into teal from left to right. */
    fun weightLine(c: EmberColors): Brush = Brush.horizontalGradient(listOf(c.water, c.weight))

    /** The area under the weight line, fading out downwards. */
    fun weightArea(c: EmberColors): Brush =
        Brush.verticalGradient(listOf(c.weight.copy(alpha = .26f), c.weight.copy(alpha = 0f)))

    /** Over-target cap on a chart bar: 45° `overLap` stripes (2.2 on 5 dp) over `surface`. */
    fun hatchOver(c: EmberColors, density: Density): Brush =
        stripes(c.overLap, c.surface, width = 2.2f, period = 5f, density)

    /** Today-so-far bar: 45° `ember2` stripes (3.5 on 7 dp) over `ember2` at 38%. */
    fun stripesToday(c: EmberColors, density: Density): Brush =
        stripes(c.ember2, c.ember2.copy(alpha = .38f), width = 3.5f, period = 7f, density)

    // A repeating linear gradient with hard stops is a stripe pattern: the web's
    // repeating-linear-gradient(-45deg, line 0 a, gap a P). The gradient runs down and to the right, so the
    // stripes themselves lean "/" as in the mock's rotate(45) SVG pattern.
    private fun stripes(ink: Color, gap: Color, width: Float, period: Float, density: Density): Brush {
        val p = with(density) { period.dp.toPx() }
        val a = (width / period).coerceIn(0f, 1f)
        val d = p / sqrt(2f)
        return Brush.linearGradient(
            0f to ink, a to ink, a to gap, 1f to gap,
            start = Offset.Zero, end = Offset(d, d), tileMode = TileMode.Repeated,
        )
    }
}

/** The four meal squircles: the same gradients in both themes, a white glyph, a soft top highlight. */
object MealTiles {
    val Breakfast: Brush = AngledGradient(arrayOf(0f to Color(0xFFFFC14D), 1f to Color(0xFFFF7A1A)), 150f)
    val Lunch: Brush = AngledGradient(arrayOf(0f to Color(0xFFFFD60A), 1f to Color(0xFFFF9F0A)), 150f)
    val Dinner: Brush = AngledGradient(arrayOf(0f to Color(0xFF9C9AFF), 1f to Color(0xFF5E5CE6)), 150f)
    val Snacks: Brush = AngledGradient(arrayOf(0f to Color(0xFF4BE0B8), 1f to Color(0xFF12A383)), 150f)

    /** The 1 dp inner highlight along the tile's top edge. */
    val Highlight: Color = Color.White.copy(alpha = .35f)

    fun brush(slot: MealSlot): Brush = when (slot) {
        MealSlot.BREAKFAST -> Breakfast
        MealSlot.LUNCH -> Lunch
        MealSlot.DINNER -> Dinner
        MealSlot.SNACK -> Snacks
    }

    fun icon(slot: MealSlot): EmberIcons = when (slot) {
        MealSlot.BREAKFAST -> EmberIcons.Sunrise
        MealSlot.LUNCH -> EmberIcons.Sun
        MealSlot.DINNER -> EmberIcons.Moon
        MealSlot.SNACK -> EmberIcons.Cookie
    }
}

// ---------------------------------------------------------------------------------------------
// Type: Inter, bundled (four static weights; the web's 650 maps to 600 for text, 700 for numerals)
// ---------------------------------------------------------------------------------------------

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Text: default tracking. Line height = size × factor, centred, not trimmed. */
private fun text(size: Int, line: Float, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = Inter, fontSize = size.sp, lineHeight = (size * line).sp, fontWeight = weight,
    letterSpacing = tracking.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Numbers: tabular figures, tighter tracking, a line box exactly one em (digit cells clip to it). */
private fun num(size: Int, weight: FontWeight = FontWeight.Bold, tracking: Float) =
    text(size, 1.0f, weight, tracking).copy(
        fontFeatureSettings = "tnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )

@Immutable
data class EmberType(
    val heroNumeral: TextStyle = num(64, tracking = -0.03f),       // ring centre "855" (web clamp 54–66)
    val weekHero: TextStyle = num(76, tracking = -0.03f),          // Daily average (web clamp 64–84)
    val display: TextStyle = num(50, tracking = -0.03f),           // sheet kcal; recipe kcal = display.copy(fontSize = 64.sp)
    val largeTitle: TextStyle = text(34, 1.12f, FontWeight.Bold, -0.026f),
    val title1: TextStyle = text(28, 1.14f, FontWeight.Bold, -0.024f),
    val title2: TextStyle = text(22, 1.2f, FontWeight.Bold, -0.021f),   // section heads, sheet titles
    val stat: TextStyle = num(28, tracking = -0.025f),             // tiles, water, weight, chart header
    val statSmall: TextStyle = num(24, tracking = -0.02f),         // macros
    val kpi: TextStyle = num(20, tracking = -0.02f),               // KPIs under the ring
    val rowNumber: TextStyle = num(16, FontWeight.SemiBold, -0.01f), // row kcal, grams
    val headline: TextStyle = text(17, 1.3f, FontWeight.SemiBold, -0.016f),
    val body: TextStyle = text(17, 1.41f, FontWeight.Normal, -0.012f),
    val callout: TextStyle = text(16, 1.3f, FontWeight.Normal, -0.01f),     // list rows (Medium for result names)
    val subhead: TextStyle = text(15, 1.3f, FontWeight.SemiBold, -0.008f),  // eyebrows, card labels, buttons (16)
    val footnote: TextStyle = text(13, 1.36f, FontWeight.Normal, -0.003f),
    val caption: TextStyle = text(12, 1.2f, FontWeight.SemiBold, 0f),       // day-marker labels, legends
    val tab: TextStyle = text(11, 1.15f, FontWeight.SemiBold, 0f),          // 10.5 on the web; 11 sp on Android
    val unitRatio: Float = 0.56f,                                  // a unit is 56% of its number, SemiBold, label2
)

val EmberTypeDefault = EmberType()

// ---------------------------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------------------------

object EmberShapes {
    /** Chips, buttons, tab bar, + button, toast, search capsule, day nav, streak chip, code pill. */
    val capsule: Shape = RoundedCornerShape(50)
    val chip: Shape = capsule
    val circle: Shape = CircleShape
    /** Text-field wells. */
    val field: Shape = RoundedCornerShape(14.dp)
    /** The amount stepper's well. */
    val amountWell: Shape = RoundedCornerShape(16.dp)
    /** Meal squircles (34 dp), segmented thumb. */
    val tileIcon: Shape = RoundedCornerShape(10.dp)
    /** Row icon tiles (30 dp). */
    val rowIcon: Shape = RoundedCornerShape(9.dp)
    val segmentThumb: Shape = RoundedCornerShape(10.dp)
    val segmentTrack: Shape = RoundedCornerShape(12.dp)
    /** Result glyph tile (40 dp). */
    val glyph: Shape = RoundedCornerShape(12.dp)
    /** The sheet header's glyph tile (46 dp). */
    val glyphLarge: Shape = RoundedCornerShape(14.dp)
    /** Cards, inset groups. */
    val card: Shape = RoundedCornerShape(24.dp)
    /** Blocks nested inside a card (insights, quick-add tiles, pairing code). */
    val nested: Shape = RoundedCornerShape(20.dp)
    val nestedSmall: Shape = RoundedCornerShape(16.dp)
    /** The floating sheet (device corner ≈ 46 minus the 8 dp inset). */
    val sheet: Shape = RoundedCornerShape(38.dp)
    /** The page while a sheet presents. */
    val receded: Shape = RoundedCornerShape(26.dp)
    /** Centred confirmations on large screens. */
    val dialog: Shape = RoundedCornerShape(28.dp)
}

// ---------------------------------------------------------------------------------------------
// Spacing and layout metrics
// ---------------------------------------------------------------------------------------------

object EmberSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s7 = 28.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp

    /** Tablets, foldables and landscape: content is centred at this width. */
    val ContentMaxWidth = 640.dp
    /** The large-title block starts this far below the status bar; 18 dp of air below it. */
    val LargeTitleTop = 14.dp
    val LargeTitleBottom = 18.dp
    val CardPadding = 18.dp
    val TilePadding = 14.dp
    val RowPaddingH = 16.dp
    /** Between cards in a stack. */
    val StackGap = 12.dp
    val SectionHeadTop = 30.dp
    val SectionHeadBottom = 12.dp
    val GroupHeadTop = 22.dp
    val GroupHeadBottom = 7.dp
    val RowMinHeight = 54.dp
    /** Material's minimum touch target (the web's 44 px grows to 48 dp). */
    val TouchTarget = 48.dp
    val TabBarHeight = 62.dp
    val TabBarSide = 16.dp
    val TabBarGap = 10.dp
    /** Above the navigation bar. */
    val TabBarBottom = 14.dp
    /** The scroll-edge fade under the tab bar, above the navigation bar. */
    val EdgeFadeHeight = 150.dp
    /** List bottom padding above the navigation bar: tab screens, stacked screens, with a docked CTA. */
    val ListBottomTab = 140.dp
    val ListBottomStacked = 24.dp
    val ListBottomDocked = 96.dp
    val DockedCtaHeight = 54.dp
    /** A docked CTA above the navigation bar on stacked screens, or above the tab bar. */
    val DockedCtaBottomStacked = 12.dp
    val DockedCtaBottomTab = 90.dp
    val SheetInset = 8.dp
    /** The sheet stops this far below the status bar. */
    val SheetTopGap = 48.dp
    val ToastBottomTab = 92.dp
    val ToastBottomStacked = 24.dp
    val CompactBarHeight = 50.dp
}

/** The side margin: 5.1% of the screen width, 16–20 dp (20 at 393 dp, 18 at 360). */
@Composable
@ReadOnlyComposable
fun rememberGutter(): Dp =
    (LocalConfiguration.current.screenWidthDp * 0.051f).coerceIn(16f, 20f).dp

// ---------------------------------------------------------------------------------------------
// Elevation: no tonal elevation anywhere; the web's CSS shadows one for one. Dark cards separate
// by tone, not shadow.
// ---------------------------------------------------------------------------------------------

enum class Elevation { Card, Float, Sheet }

fun Modifier.emberShadow(level: Elevation, shape: Shape, dark: Boolean): Modifier = when (level) {
    Elevation.Card -> if (dark) this else this
        .dropShadow(shape, Shadow(radius = 2.dp, color = Color.Black.copy(.05f), offset = DpOffset(0.dp, 1.dp)))
        .dropShadow(shape, Shadow(radius = 14.dp, spread = (-6).dp, color = Color.Black.copy(.10f), offset = DpOffset(0.dp, 4.dp)))
    Elevation.Float -> this
        .dropShadow(
            shape,
            Shadow(
                radius = 32.dp, spread = if (dark) (-8).dp else (-10).dp,
                color = Color.Black.copy(if (dark) .70f else .24f), offset = DpOffset(0.dp, 12.dp),
            ),
        )
        .then(
            if (dark) Modifier
            else Modifier.dropShadow(shape, Shadow(radius = 6.dp, color = Color.Black.copy(.05f), offset = DpOffset(0.dp, 2.dp))),
        )
    Elevation.Sheet -> this
        .dropShadow(
            shape,
            Shadow(radius = 60.dp, spread = (-12).dp, color = Color.Black.copy(if (dark) .80f else .30f), offset = DpOffset(0.dp, 18.dp)),
        )
}
