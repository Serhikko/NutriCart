package com.nutricart.app.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import com.nutricart.app.R
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberColors
import com.nutricart.app.ui.ember.EmberDark
import com.nutricart.app.ui.ember.EmberLight
import com.nutricart.app.widget.WidgetContent
import com.nutricart.app.widget.WidgetData
import com.nutricart.app.widget.WidgetRing
import com.nutricart.app.widget.WidgetSizes
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The platform surfaces (APP-DESIGN.md §6): the home-screen widget rendered through Glance's own
 * RemoteViews (the views a launcher inflates, in every size class and state), the widget ring's
 * layers, the adaptive and themed launcher icon, and the notification icon. Compare with
 * app-design/shots/extras-{light,dark}.png.
 */
class EmberPlatformScreenshots(private val variant: Variant) : ScreenshotTest(variant) {

    @Test
    @KeyScreen
    fun widget() {
        val cells = listOf(
            listOf(cell(WidgetSizes.Wide, 180, 84, Day.Under), cell(WidgetSizes.Square, 127, 127, Day.Under)),
            listOf(cell(WidgetSizes.Large, 180, 180, Day.Over), cell(WidgetSizes.Compact, 127, 84, Day.Under)),
            listOf(cell(WidgetSizes.Wide, 180, 84, null), cell(WidgetSizes.Square, 127, 127, null)),
        )
        shoot("platform-widget") { WidgetBoard(cells) }
    }

    @Test
    fun widgetMore() {
        val cells = listOf(
            listOf(cell(WidgetSizes.Large, 180, 180, Day.Under), cell(WidgetSizes.Square, 127, 127, Day.Over)),
            listOf(cell(WidgetSizes.Wide, 180, 84, Day.Over), cell(WidgetSizes.Compact, 127, 84, Day.Over)),
            listOf(cell(WidgetSizes.Wide, 180, 84, Day.Empty), cell(WidgetSizes.Compact, 127, 84, null)),
            listOf(cell(WidgetSizes.Large, 180, 180, null), cell(WidgetSizes.Square, 127, 127, Day.Double)),
        )
        shoot("platform-widget-more") { WidgetBoard(cells) }
    }

    @Test
    @Tall(1900)
    fun widgetRings() {
        val context = compose.activity
        val dark = context.resources.configuration.isNightModeActive
        // The ring layers themselves, flattened exactly as the widget's ImageViews stack them, as PNGs.
        val rings = RINGS.flatMap { (size, stroke) ->
            PROGRESS.map { p -> Triple(size, p, flatten(context, size, stroke, p, dark)) }
        }
        val root = System.getProperty("nutricart.screenshots.dir") ?: "build/outputs/screenshots"
        val dir = File(root, "${variant.dir}/widget-ring")
        dir.mkdirs()
        rings.forEach { (size, p, bitmap) ->
            File(dir, "ring-${size.toInt()}dp-p${(p * 100).roundToInt()}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        shoot("platform-widget-rings") { RingsBoard(rings) }
    }

    @Test
    fun icons() = shoot("platform-icons") { IconsBoard() }

    // -----------------------------------------------------------------------------------------
    // The widget, through Glance
    // -----------------------------------------------------------------------------------------

    private enum class Day(val data: WidgetData) {
        Under(WidgetData(remainingKcal = 855, eatenKcal = 1385, targetKcal = 2240)),
        Over(WidgetData(remainingKcal = -290, eatenKcal = 2530, targetKcal = 2240)),
        Empty(WidgetData(remainingKcal = 2240, eatenKcal = 0, targetKcal = 2240)),
        Double(WidgetData(remainingKcal = -2900, eatenKcal = 5140, targetKcal = 2240)),
    }

    /** One widget on the board: Glance's RemoteViews for one size class, shown at [w] x [h] dp. */
    private class Cell(val w: Dp, val h: Dp, val views: RemoteViews)

    private fun cell(sizeClass: DpSize, w: Int, h: Int, day: Day?): Cell =
        Cell(w.dp, h.dp, remoteViews(compose.activity, day?.data, sizeClass))

    /** The widget's content composed by Glance into RemoteViews, as the launcher receives them. */
    @OptIn(ExperimentalGlanceApi::class)
    private fun remoteViews(context: Context, data: WidgetData?, sizeClass: DpSize): RemoteViews {
        // The real widget reads its data through Hilt; this one is handed the day directly.
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetContent(data) }
        }
        return runBlocking { widget.compose(context, size = sizeClass) }
    }

    @Composable
    private fun WidgetBoard(rows: List<List<Cell>>) {
        Page {
            Text("Home-screen widget", style = Ember.type.title2, color = Ember.colors.label)
            // A wallpaper stand-in (the mock's), so the card's own colours are judged on a real backdrop.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(wallpaper(Ember.colors)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { WidgetView(it) } }
                }
            }
        }
    }

    @Composable
    private fun WidgetView(cell: Cell) {
        // Android 12+ launchers round the widget with the system radius (the card asks for it with
        // cornerRadius); the host does the clipping, so the board does it here.
        val radius = with(LocalDensity.current) {
            LocalResources.current.getDimension(android.R.dimen.system_app_widget_background_radius).toDp()
        }
        AndroidView(
            factory = { context -> cell.views.apply(context, FrameLayout(context)) },
            modifier = Modifier.size(cell.w, cell.h).clip(RoundedCornerShape(radius)),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The ring layers
    // -----------------------------------------------------------------------------------------

    /** Track, Ember progress and ink lap stacked on the card colour, with ImageView's tint (SRC_ATOP). */
    private fun flatten(context: Context, size: Float, stroke: Float, p: Float, dark: Boolean): Bitmap {
        val c = if (dark) EmberDark else EmberLight
        val track = WidgetRing.bitmap(context, WidgetRing.Layer.Track, size, stroke, p)
        val out = Bitmap.createBitmap(track.width, track.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(c.surface.toArgb())
        fun tinted(color: Color) = Paint().apply {
            colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
        val trackColor = c.emberTrack.compositeOver(c.surface)
        canvas.drawBitmap(track, 0f, 0f, tinted(trackColor))
        canvas.drawBitmap(WidgetRing.bitmap(context, WidgetRing.Layer.Progress, size, stroke, p), 0f, 0f, null)
        canvas.drawBitmap(WidgetRing.bitmap(context, WidgetRing.Layer.Lap, size, stroke, p), 0f, 0f, tinted(c.overLap))
        return out
    }

    @Composable
    private fun RingsBoard(rings: List<Triple<Float, Float, Bitmap>>) {
        Page {
            Text("Widget ring", style = Ember.type.title2, color = Ember.colors.label)
            RINGS.forEach { (size, _) ->
                EmberCard {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.Bottom,
                    ) {
                        rings.filter { it.first == size }.forEach { (_, p, bitmap) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PixelImage(bitmap)
                                Text("${(p * 100).roundToInt()}%", style = Ember.type.caption, color = Ember.colors.label2)
                            }
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Launcher icon and notification icon
    // -----------------------------------------------------------------------------------------

    @Composable
    private fun IconsBoard() {
        val context = LocalContext.current
        val c = Ember.colors
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val px = with(LocalDensity.current) { 56.dp.roundToPx() }
        val big = with(LocalDensity.current) { 96.dp.roundToPx() }
        Page {
            Text("Launcher icon", style = Ember.type.title2, color = c.label)
            EmberCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Figure(masked(icon, px, Mask.Circle), "circle")
                    Figure(masked(icon, px, Mask.Squircle), "squircle")
                    Figure(masked(icon, px, Mask.Rounded), "rounded")
                    Figure(masked(icon, px, Mask.Circle, themed = THEMED_LIGHT), "themed")
                    Figure(masked(icon, px, Mask.Circle, themed = THEMED_DARK), "themed dark")
                }
            }
            EmberCard {
                // The whole 108 dp canvas of each layer: the mask shows the inner 72 dp, the safe zone
                // is the 66 dp circle (both outlined).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Figure(layers(icon, big, Layers.Background), "background")
                    Figure(layers(icon, big, Layers.Foreground), "foreground")
                    Figure(layers(icon, big, Layers.Monochrome), "monochrome")
                }
            }
            Text("Notifications", style = Ember.type.title2, color = c.label)
            Notification(
                header = "· now",
                title = stringResource(R.string.reminder_title),
                text = stringResource(R.string.reminder_text, stringResource(R.string.meal_lunch)),
            )
            Notification(
                header = "· " + stringResource(R.string.partner_channel_name) + " · 2 min",
                title = "Olena",
                text = stringResource(R.string.partner_nudge_default),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // The status bar draws the small icon flat white on whatever is behind it.
                Box(
                    Modifier.size(width = 120.dp, height = 32.dp).clip(CircleShape).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_notification), null, Modifier.size(24.dp))
                }
                Text("status bar, 24 dp", style = Ember.type.footnote, color = c.label2)
            }
        }
    }

    @Composable
    private fun Notification(header: String, title: String, text: String) {
        val c = Ember.colors
        EmberCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The shade shows the small icon on the notification's colour (setColor); the mock
                // keeps the light value for the disc, so does this illustration.
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(EmberLight.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_notification), null, Modifier.size(18.dp))
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = Ember.type.caption,
                            color = colorResource(R.color.ember_notification),
                        )
                        Text(header, style = Ember.type.caption.copy(fontWeight = FontWeight.Medium), color = c.label2)
                    }
                    Text(title, style = Ember.type.subhead, color = c.label, modifier = Modifier.padding(top = 4.dp))
                    Text(text, style = Ember.type.callout, color = c.label2)
                }
            }
        }
    }

    private enum class Mask { Circle, Squircle, Rounded }
    private enum class Layers { Background, Foreground, Monochrome }

    /**
     * The adaptive icon as a launcher shows it: both 108-unit layers scaled so the central 72 units
     * fill [px], cut by [mask] with an anti-aliased edge. [themed] = (background, glyph) draws the
     * monochrome layer tinted the way Android 13+ themed icons do.
     */
    private fun masked(icon: AdaptiveIconDrawable, px: Int, mask: Mask, themed: Pair<Int, Int>? = null): Bitmap {
        val art = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(art)
        val layer = (px * 108f / 72f).roundToInt()
        val o = (px - layer) / 2
        if (themed == null) {
            drawLayer(icon.background, canvas, o, layer)
            drawLayer(icon.foreground, canvas, o, layer)
        } else {
            canvas.drawColor(themed.first)
            val mono = requireNotNull(icon.monochrome) { "ic_launcher has no monochrome layer" }.mutate()
            mono.setTint(themed.second)
            drawLayer(mono, canvas, o, layer)
        }
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawPath(maskPath(mask, px.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(art, 0f, 0f, paint)
        return out
    }

    private fun layers(icon: AdaptiveIconDrawable, px: Int, which: Layers): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        when (which) {
            Layers.Background -> drawLayer(icon.background, canvas, 0, px)
            Layers.Foreground -> {
                canvas.drawColor(EmberDark.surface3.toArgb())
                drawLayer(icon.foreground, canvas, 0, px)
            }
            Layers.Monochrome -> {
                canvas.drawColor(EmberDark.surface3.toArgb())
                drawLayer(requireNotNull(icon.monochrome).mutate(), canvas, 0, px)
            }
        }
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = px / 108f * .5f
            color = Color.White.copy(alpha = .6f).toArgb()
            pathEffect = DashPathEffect(floatArrayOf(px / 108f * 2f, px / 108f * 2f), 0f)
        }
        val u = px / 108f
        canvas.drawRect(RectF(18 * u, 18 * u, 90 * u, 90 * u), guide)
        canvas.drawCircle(54 * u, 54 * u, 33 * u, guide)
        return out
    }

    private fun drawLayer(d: Drawable, canvas: Canvas, offset: Int, size: Int) {
        d.setBounds(offset, offset, offset + size, offset + size)
        d.draw(canvas)
    }

    /** Launcher masks over a [s] px square: a circle, a superellipse squircle, a rounded square. */
    private fun maskPath(mask: Mask, s: Float): Path = Path().apply {
        when (mask) {
            Mask.Circle -> addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
            Mask.Rounded -> addRoundRect(RectF(0f, 0f, s, s), s * .22f, s * .22f, Path.Direction.CW)
            Mask.Squircle -> {
                val r = s / 2f
                for (i in 0..360) {
                    val t = Math.toRadians(i.toDouble())
                    val x = r + r * sign(cos(t)) * abs(cos(t)).pow(2.0 / 5.0)
                    val y = r + r * sign(sin(t)) * abs(sin(t)).pow(2.0 / 5.0)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
        }
    }

    // -----------------------------------------------------------------------------------------

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxSize().background(Ember.colors.bg).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }

    @Composable
    private fun Figure(bitmap: Bitmap, label: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelImage(bitmap)
            Text(label, style = Ember.type.footnote, color = Ember.colors.label2, modifier = Modifier.padding(top = 6.dp))
        }
    }

    /** A bitmap at one bitmap pixel per screen pixel. */
    @Composable
    private fun PixelImage(bitmap: Bitmap) {
        val size = with(LocalDensity.current) { bitmap.width.toDp() }
        Image(bitmap.asImageBitmap(), null, Modifier.size(size))
    }

    private companion object {
        // Sizes the widget draws (diameter, stroke in dp) and the states worth seeing.
        val RINGS = listOf(46f to 6f, 84f to 10f, 116f to 13f)
        val PROGRESS = listOf(0f, .25f, .62f, .95f, 1f, 1.13f, 1.6f)

        // Stand-ins for the launcher: the mock's wallpaper and a Material You themed-icon palette.
        // Not app colours, so they are not Ember tokens.
        fun wallpaper(c: EmberColors): Brush = if (c.isDark) {
            Brush.linearGradient(0f to Color(0xFF2A2320), .6f to Color(0xFF121218), 1f to Color(0xFF0B0B10))
        } else {
            Brush.linearGradient(0f to Color(0xFF8A6E5A), .6f to Color(0xFF3C3A52), 1f to Color(0xFF1F2433))
        }

        val THEMED_LIGHT = Color(0xFFD9E2EC).toArgb() to Color(0xFF2E3440).toArgb()
        val THEMED_DARK = Color(0xFF2E3440).toArgb() to Color(0xFFD9E2EC).toArgb()
    }
}
