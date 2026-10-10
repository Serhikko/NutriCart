package com.nutricart.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.nutricart.app.ui.ember.EmberLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Ember day ring for the home-screen widget. Glance cannot draw paths, so the ring is drawn here
 * with android.graphics, built exactly like the app's Ring (ui/ember/Ring.kt, APP-DESIGN.md §3.4 R):
 * two butt-ended halves whose vertical gradients are flat across 6 o'clock, round ends drawn as
 * circles in the same gradients, a soft shade ahead of the end cap near closure, a second lap past
 * the goal.
 *
 * A bitmap cannot follow day and night, but an ImageView's colour filter can (Glance passes a day
 * and a night colour on Android 12+). So the ring comes in layers that the widget stacks:
 * - [Layer.Track] and [Layer.Lap] are white masks, tinted by the widget with the theme's track and
 *   ink colours, so the second lap is ink on white and near-white on graphite, as in the app;
 * - [Layer.Progress] carries the Ember gradient in the light-theme colours, which read on both the
 *   white and the graphite card.
 */
internal object WidgetRing {

    enum class Layer { Track, Progress, Lap }

    // Two laps is the most the ring can show (the second one in ink), as in the app.
    private const val MAX_P = 2f

    // Keyed by layer, pixel size, stroke and progress rounded to 0.5%: a refresh with the same numbers
    // reuses its bitmaps. Sized in bytes; a 108 dp layer at 3.5x is about 0.6 MB.
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The fraction of the goal a day shows: eaten / target, 0 without a target, at most two laps. */
    fun progress(eaten: Int, target: Int): Float =
        if (target > 0) (eaten.toFloat() / target).coerceIn(0f, MAX_P) else 0f

    /**
     * One [layer] of a ring [sizeDp] across with a [strokeDp] stroke at progress [p] (1 = the goal),
     * at the device's density. [Layer.Lap] is empty (fully transparent) until [p] passes 1.
     */
    fun bitmap(context: Context, layer: Layer, sizeDp: Float, strokeDp: Float, p: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val strokePx = strokeDp * density
        // Rounded so neighbouring values share a bitmap; the track does not depend on progress.
        val q = if (layer == Layer.Track) 0f else (p.coerceIn(0f, MAX_P) * 200f).roundToInt() / 200f
        val key = "$layer/$sizePx/$strokePx/$q"
        cache.get(key)?.let { return it }
        return draw(layer, sizePx, strokePx, q).also { cache.put(key, it) }
    }

    private fun draw(layer: Layer, sizePx: Int, s: Float, p: Float): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val d = sizePx.toFloat()
        val r = (d - s) / 2f
        val c = d / 2f
        val oval = RectF(s / 2f, s / 2f, d - s / 2f, d - s / 2f)

        // The point [t] of a turn clockwise from 12 o'clock.
        fun at(t: Float): PointF {
            val a = t * 2f * PI.toFloat()
            return PointF(c + r * sin(a), c - r * cos(a))
        }

        when (layer) {
            Layer.Track -> canvas.drawCircle(c, c, r, stroke(s, Paint.Cap.BUTT))
            Layer.Lap -> if (p > 1f) {
                // Past the goal: the second lap, tinted ink by the widget.
                canvas.drawArc(oval, -90f, 360f * min(p - 1f, 1f), false, stroke(s, Paint.Cap.ROUND))
            }
            Layer.Progress -> if (p > 0f) {
                val ember = EmberLight
                // The right half runs amber to vermilion top to bottom, the left half raspberry to
                // vermilion: both are vermilion across 6 o'clock, so they meet without a seam.
                val a = LinearGradient(0f, s, 0f, d - s, ember.ember1.toArgb(), ember.ember2.toArgb(), Shader.TileMode.CLAMP)
                val b = LinearGradient(0f, s, 0f, d - s, ember.ember3.toArgb(), ember.ember2.toArgb(), Shader.TileMode.CLAMP)
                // The right half runs 0.4% under the left one, so no hairline shows where they meet.
                canvas.drawArc(oval, -90f, 360f * min(p, .504f), false, stroke(s, Paint.Cap.BUTT, a))
                if (p > .5f) {
                    canvas.drawArc(oval, 90f, 360f * (p - .5f).coerceAtMost(.5f), false, stroke(s, Paint.Cap.BUTT, b))
                }
                val start = at(0f)
                canvas.drawCircle(start.x, start.y, s / 2f, fill(a))
                val end = min(p, 1f)
                if (p > .9f) {
                    // Near closure a soft shade just ahead of the end cap, so the cap reads as lying
                    // over the start rather than merging into it.
                    val ahead = at(end + .012f)
                    val radius = s * .62f
                    val shade = RadialGradient(
                        ahead.x, ahead.y, radius,
                        intArrayOf(shadeColor, shadeColor, Color.Transparent.toArgb()),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
                    )
                    val paint = fill(shade).apply { alpha = (((p - .9f) * 8f).coerceIn(0f, 1f) * 255).roundToInt() }
                    canvas.drawCircle(ahead.x, ahead.y, radius, paint)
                }
                val tip = at(end)
                canvas.drawCircle(tip.x, tip.y, s / 2f, fill(if (end <= .5f) a else b))
            }
        }
        return bitmap
    }

    private val shadeColor = Color.Black.copy(alpha = .55f).toArgb()

    private fun stroke(width: Float, cap: Paint.Cap, shader: Shader? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = cap
        color = Color.White.toArgb()
        this.shader = shader
    }

    private fun fill(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.shader = shader
    }
}
