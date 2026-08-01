package com.nutricart.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The dashboard's hero: a 270° gauge that fills toward the daily target.
 * The sweep is ANIMATED — every change (each logged meal) rolls the arc
 * smoothly instead of jumping. Over 100% the ring turns to the error color.
 * [content] is drawn in the middle (the remaining-kcal counter).
 */
@Composable
fun CalorieRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ringProgress",
    )
    val overTarget = progress > 1f
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor =
        if (overTarget) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
            val inset = ringWidth.toPx() / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // Gauge opening faces down: start at 135°, sweep up to 270°.
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = barColor,
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        content()
    }
}
