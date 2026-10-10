package com.nutricart.app.ui.ember

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

// Draws the generated Ember glyphs (EmberIcons.kt): stroked line art on a 24 grid, round caps and
// joins, the same artwork as the website. Material icons are not used by new code.

/**
 * The stroke (in 24-grid units) for a glyph drawn at [size], the web's rule: smaller glyphs get a
 * heavier stroke so they read the same. 16 → 2.4, 20 → 2.1, 24 → 1.9, tab icons 25 → 1.85,
 * 28 → 2.2, the + button 30 → 2.6.
 */
fun emberStrokeFor(size: Dp): Float {
    val s = size.value
    return when {
        s <= 16f -> 2.4f
        s <= 20f -> 2.4f - (s - 16f) / 4f * .3f
        s <= 24f -> 2.1f - (s - 20f) / 4f * .2f
        s < 26.5f -> 1.85f
        s < 29f -> 2.2f
        s < 31f -> 2.6f
        else -> 2.6f * 30f / s   // keep about 2.6 dp of line on bigger art (empty states)
    }
}

private data class VectorKey(val icon: EmberIcons, val stroke: Float, val filled: Boolean)

private val vectors = ConcurrentHashMap<VectorKey, ImageVector>()

/** The glyph as an [ImageVector] in black (tint it), built once per (glyph, stroke, filled) and cached. */
fun EmberIcons.toImageVector(strokeWidth: Float, filled: Boolean = false): ImageVector =
    vectors.getOrPut(VectorKey(this, strokeWidth, filled)) {
        val builder = ImageVector.Builder(
            name = "ember.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            // Direction glyphs flip in right-to-left layouts.
            autoMirror = this == EmberIcons.Left || this == EmberIcons.Right,
        )
        for (p in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(p.d).toNodes(),
                fill = if (filled && p.dot == null) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = p.dot ?: strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.build()
    }

/**
 * One Ember glyph at [size] (24 dp by default). [contentDescription] is null for decorative glyphs:
 * the control around it carries the name. [tint] colours it; [brush] paints it with a gradient
 * instead (the Ember gradient on the active tab, the + button, the toast check, the streak flame).
 * [filled] fills the outline too (the favourite star, the locked lock).
 */
@Composable
fun EmberIcon(
    icon: EmberIcons,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    brush: Brush? = null,
    filled: Boolean = false,
    strokeWidth: Float? = null,
) {
    val stroke = strokeWidth ?: emberStrokeFor(size)
    val vector = remember(icon, stroke, filled) { icon.toImageVector(stroke, filled) }
    val painter = rememberVectorPainter(vector)
    val described = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    val paintModifier = if (brush != null) {
        // Draw the glyph in black offscreen, then keep the gradient only where the glyph is.
        Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush, blendMode = BlendMode.SrcIn)
            }
            .paint(painter, contentScale = ContentScale.Fit)
    } else {
        Modifier.paint(
            painter,
            contentScale = ContentScale.Fit,
            colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
        )
    }
    // Fit, not paint()'s default Inside: Inside only ever shrinks, so the 24 dp vector would stay 24 dp
    // inside a 30 dp + button or a 44 dp empty-state glyph.
    Box(modifier.then(described).size(size).then(paintModifier))
}

/**
 * A decorative glyph whose [tint] is read when it is drawn, not when it is composed: for a colour
 * that animates (a plan tool turning on), so the fade redraws the glyph without recomposing it.
 */
@Composable
internal fun EmberGlyph(
    icon: EmberIcons,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    filled: Boolean = false,
    strokeWidth: Float? = null,
) {
    val stroke = strokeWidth ?: emberStrokeFor(size)
    val vector = remember(icon, stroke, filled) { icon.toImageVector(stroke, filled) }
    val painter = rememberVectorPainter(vector)
    Box(
        modifier
            .size(size)
            .drawWithCache {
                // The filter is rebuilt only when the colour changes (the cache re-runs on that read).
                val color = tint()
                val filter = if (color == Color.Unspecified) null else ColorFilter.tint(color)
                onDrawBehind { with(painter) { draw(this@onDrawBehind.size, colorFilter = filter) } }
            },
    )
}
