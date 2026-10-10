package com.nutricart.app.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nutricart.app.MainActivity
import com.nutricart.app.R
import com.nutricart.app.ui.ember.EmberDark
import com.nutricart.app.ui.ember.EmberLight
import com.nutricart.app.ui.ember.toColorScheme
import dagger.hilt.android.EntryPointAccessors
import java.text.NumberFormat

/**
 * "Left today" on the home screen, in Ember: the day ring and the number you act on, on a white
 * (graphite at night) card like the app's, never the wallpaper's colours. The system refreshes it
 * every 30 min (widget_info.xml); MainActivity additionally refreshes it whenever the app goes to the
 * background, so a just-logged meal shows up immediately.
 */
class NutriCartWidget : GlanceAppWidget() {

    // One layout per size class, all sent at once: the launcher switches between them as the user
    // resizes, without asking the app again.
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance objects are created by the system, not by Hilt — the
        // EntryPoint is the documented way to reach injected classes.
        val data = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .widgetDataSource()
            .today()
        provideContent { WidgetContent(data) }
    }
}

/** The manifest-registered receiver; all real logic lives in the widget. */
class NutriCartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutriCartWidget()
}

/** The size classes the widget lays out for (APP-DESIGN.md §6.1). */
internal object WidgetSizes {
    /** The number and its caption, no ring. */
    val Compact = DpSize(110.dp, 40.dp)

    /** A 46 dp ring beside the number, its caption and the eaten line. */
    val Wide = DpSize(180.dp, 40.dp)

    /**
     * An 84 dp ring with the number inside. The widget's smallest square is 110 dp (minWidth), so
     * the ring fills it as the mock's 58 dp ring fills its 76 dp tile.
     */
    val Square = DpSize(110.dp, 110.dp)

    /** A 116 dp ring with the number and caption inside, the eaten line under it. */
    val Large = DpSize(180.dp, 180.dp)

    val all = setOf(Compact, Wide, Square, Large)

    /**
     * Which class [size] gets. With SizeMode.Responsive, [size] is one of [all]; at any other size
     * (a preview at an exact size) this picks what the launcher would: the largest class that fits.
     */
    fun of(size: DpSize): DpSize = when {
        size.width >= Large.width && size.height >= Large.height -> Large
        size.height >= Square.height -> Square
        size.width >= Wide.width -> Wide
        else -> Compact
    }
}

/** A ring's drawn diameter and stroke in the widget (the stroke keeps the app's ~12% proportion). */
private class WidgetRingSize(val size: Dp, val stroke: Dp)

private val RingWide = WidgetRingSize(46.dp, 6.dp)
private val RingSquare = WidgetRingSize(84.dp, 10.dp)
private val RingLarge = WidgetRingSize(116.dp, 13.dp)
private val RingSetUp = WidgetRingSize(44.dp, 6.dp)
private val RingSetUpSquare = WidgetRingSize(48.dp, 6.dp)

/**
 * Ember colours with day and night values. Glance hands both to RemoteViews (Android 12+), so the
 * widget follows the system theme by itself, without being redrawn.
 */
private object WidgetColors {
    val surface = ColorProvider(day = EmberLight.surface, night = EmberDark.surface)
    val label = ColorProvider(day = EmberLight.label, night = EmberDark.label)
    val label2 = ColorProvider(day = EmberLight.label2, night = EmberDark.label2)

    // The track token is translucent; an ImageView colour filter keeps the bitmap's alpha, so the
    // track is laid over the card colour here and tinted opaque (the same pixels on the card).
    val track = ColorProvider(
        day = EmberLight.emberTrack.compositeOver(EmberLight.surface),
        night = EmberDark.emberTrack.compositeOver(EmberDark.surface),
    )

    // The second lap past the goal: ink, never another warm hue.
    val lap = ColorProvider(day = EmberLight.overLap, night = EmberDark.overLap)

    // Every Material role mapped from Ember, so nothing a Glance default draws is wallpaper-coloured.
    val theme = ColorProviders(light = EmberLight.toColorScheme(), dark = EmberDark.toColorScheme())
}

/**
 * The widget's type. RemoteViews cannot use the app's Inter or Ember.type, so these are Glance styles
 * in the launcher's own sans-serif, sized from the mock (numbers bold, captions medium, label2).
 *
 * Text follows the user's font size up to 1.3x and stops there: the launcher's cells do not grow with
 * the text, and a clipped number helps nobody. The app itself scales to 2x.
 */
private class WidgetType(context: Context) {
    private val metrics = context.resources.displayMetrics
    private val fontScale = context.resources.configuration.fontScale

    // The same display at font scale 1.3: its metrics carry the platform's own sp conversion for 1.3
    // (non-linear on Android 14+, where large text grows less than small text).
    private val cappedMetrics: DisplayMetrics? = if (fontScale > MAX_SCALE) {
        val config = Configuration(context.resources.configuration).apply { fontScale = MAX_SCALE }
        context.createConfigurationContext(config).resources.displayMetrics
    } else {
        null
    }

    /** Room for the third line of the wide layout: text no larger than about 1.15x. */
    val roomy = fontScale <= 1.15f

    /** [base] sp, or the smaller sp value that renders as [base] does at font scale 1.3. */
    private fun size(base: Float): TextUnit {
        val capped = cappedMetrics ?: return base.sp
        val target = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, base, capped)
        var low = 1f
        var high = base
        repeat(16) {
            val mid = (low + high) / 2f
            if (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mid, metrics) < target) low = mid else high = mid
        }
        return low.sp
    }

    fun number(base: Float, align: TextAlign = TextAlign.Start) =
        TextStyle(color = WidgetColors.label, fontSize = size(base), fontWeight = FontWeight.Bold, textAlign = align)

    fun caption(base: Float = 12f, align: TextAlign = TextAlign.Start, color: ColorProvider = WidgetColors.label2) =
        TextStyle(color = color, fontSize = size(base), fontWeight = FontWeight.Medium, textAlign = align)

    fun meta(align: TextAlign = TextAlign.Start) =
        TextStyle(color = WidgetColors.label2, fontSize = size(11f), fontWeight = FontWeight.Normal, textAlign = align)

    private companion object {
        const val MAX_SCALE = 1.3f
    }
}

/** What one day shows, already in words (formatted for the device's language). */
private class WidgetDay(
    val p: Float,
    val number: String,
    val caption: String,
    val eatenLine: String,
    val dayLine: String,
)

private fun WidgetData.toDay(context: Context): WidgetDay {
    val format = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    val over = remainingKcal < 0
    // Over the goal the number is the overshoot (positive) and the caption says "over".
    val number = format.format(if (over) -remainingKcal else remainingKcal)
    val eaten = format.format(eatenKcal)
    val target = format.format(targetKcal)
    return WidgetDay(
        p = WidgetRing.progress(eatenKcal, targetKcal),
        number = number,
        caption = context.getString(if (over) R.string.day_kcal_over else R.string.day_kcal_left),
        eatenLine = context.getString(R.string.widget_eaten_line, eaten, target),
        dayLine = context.getString(
            if (over) R.string.ring_day_line_over else R.string.ring_day_line, eaten, target, number,
        ),
    )
}

/** The widget's content for [data] (null = onboarding not finished). Internal for the screenshot tests. */
@Composable
internal fun WidgetContent(data: WidgetData?) {
    GlanceTheme(colors = WidgetColors.theme) {
        val context = LocalContext.current
        val type = WidgetType(context)
        val day = data?.toDay(context)
        val sizeClass = WidgetSizes.of(LocalSize.current)
        val padding = if (sizeClass == WidgetSizes.Wide) {
            // A one-row widget is often under 80 dp tall: three lines of text need the height.
            GlanceModifier.padding(start = 12.dp, top = 10.dp, end = 16.dp, bottom = 10.dp)
        } else {
            GlanceModifier.padding(if (sizeClass == WidgetSizes.Large) 14.dp else 12.dp)
        }
        // The whole card opens the app and reads as the day line ("1,385 of 2,240 kcal eaten, 855 left").
        var root = GlanceModifier.fillMaxSize().appWidgetBackground().then(cardBackground())
            .clickable(actionStartActivity<MainActivity>())
        if (day != null) root = root.semantics { contentDescription = day.dayLine }
        Box(root.then(padding), contentAlignment = Alignment.Center) {
            when {
                day == null -> SetUp(type, sizeClass, context.getString(R.string.widget_no_data))
                sizeClass == WidgetSizes.Large -> LargeDay(type, day)
                sizeClass == WidgetSizes.Square -> SquareDay(type, day)
                sizeClass == WidgetSizes.Wide -> WideDay(type, day)
                else -> CompactDay(type, day)
            }
        }
    }
}

// Android 12+ rounds the card with the launcher's own widget radius; before that a 16 dp rounded
// shape, tinted with the same day/night card colour.
private fun cardBackground(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.background(WidgetColors.surface)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(
            ImageProvider(R.drawable.widget_background),
            colorFilter = ColorFilter.tint(WidgetColors.surface),
        )
    }

/** The ring as stacked layers (track, Ember progress, ink lap) with [center] on top. */
@Composable
private fun DayRing(p: Float, ring: WidgetRingSize, center: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val size = ring.size.value
    val stroke = ring.stroke.value
    val layer = GlanceModifier.size(ring.size)
    Box(layer, contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(WidgetRing.bitmap(context, WidgetRing.Layer.Track, size, stroke, p)),
            contentDescription = null, modifier = layer, colorFilter = ColorFilter.tint(WidgetColors.track),
        )
        if (p > 0f) {
            Image(
                ImageProvider(WidgetRing.bitmap(context, WidgetRing.Layer.Progress, size, stroke, p)),
                contentDescription = null, modifier = layer,
            )
        }
        if (p > 1f) {
            Image(
                ImageProvider(WidgetRing.bitmap(context, WidgetRing.Layer.Lap, size, stroke, p)),
                contentDescription = null, modifier = layer, colorFilter = ColorFilter.tint(WidgetColors.lap),
            )
        }
        center()
    }
}

/** Text inside a ring wraps within the hole, clear of the stroke. */
private fun WidgetRingSize.hole(): GlanceModifier = GlanceModifier.width(size - stroke * 2 - 10.dp)

@Composable
private fun CompactDay(type: WidgetType, day: WidgetDay) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.number, style = type.number(26f, TextAlign.Center), maxLines = 1)
        Text(day.caption, style = type.caption(align = TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun WideDay(type: WidgetType, day: WidgetDay) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        DayRing(day.p, RingWide)
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(day.number, style = type.number(24f), maxLines = 1)
            Text(day.caption, style = type.caption(), maxLines = 1)
            // With large text the eaten line goes, so the number and its caption never clip.
            if (type.roomy) Text(day.eatenLine, style = type.meta(), maxLines = 1)
        }
    }
}

@Composable
private fun SquareDay(type: WidgetType, day: WidgetDay) {
    DayRing(day.p, RingSquare) {
        // "2,240" needs a step down to stay clear of the stroke.
        val size = if (day.number.length > 4) 19f else 22f
        Text(day.number, style = type.number(size, TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun LargeDay(type: WidgetType, day: WidgetDay) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DayRing(day.p, RingLarge) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.number, style = type.number(24f, TextAlign.Center), maxLines = 1)
                Text(day.caption, style = type.caption(align = TextAlign.Center), maxLines = 2, modifier = RingLarge.hole())
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(day.eatenLine, style = type.meta(TextAlign.Center), maxLines = 1)
    }
}

/** Onboarding not finished: an empty ring and the invitation to open the app. */
@Composable
private fun SetUp(type: WidgetType, sizeClass: DpSize, text: String) {
    when (sizeClass) {
        WidgetSizes.Compact ->
            Text(text, style = type.caption(align = TextAlign.Center, color = WidgetColors.label), maxLines = 3)
        WidgetSizes.Wide -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            DayRing(0f, RingSetUp)
            Spacer(GlanceModifier.width(12.dp))
            Text(text, style = type.caption(color = WidgetColors.label), maxLines = 3)
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val large = sizeClass == WidgetSizes.Large
            DayRing(0f, if (large) RingSquare else RingSetUpSquare)
            Spacer(GlanceModifier.height(if (large) 10.dp else 8.dp))
            Text(
                text,
                style = type.caption(if (large) 12f else 11f, TextAlign.Center, WidgetColors.label),
                maxLines = 2,
            )
        }
    }
}
