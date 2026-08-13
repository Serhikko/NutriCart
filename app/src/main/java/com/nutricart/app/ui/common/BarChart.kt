package com.nutricart.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.nutricart.app.domain.logic.AdherenceCalculator

/** One bar: the day's eaten kcal plus its adherence verdict (= bar color). */
data class BarPoint(
    val value: Float,
    val state: AdherenceCalculator.DayState,
)

/**
 * Plain Canvas bar chart (same approach as WeightChart — no chart library).
 * Bars are colored by the day's verdict: green = on plan, red = over,
 * neutral = nothing logged. [targetLine] draws a dashed reference line.
 */
@Composable
fun BarChart(
    points: List<BarPoint>,
    targetLine: Float?,
    modifier: Modifier = Modifier,
) {
    val goodColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        // The tallest of bars-or-line defines the scale; 1f avoids /0.
        val maxValue = maxOf(points.maxOf { it.value }, targetLine ?: 0f, 1f)
        val slotWidth = size.width / points.size
        // Thin gaps for long ranges, wider bars for a week.
        val barWidth = slotWidth * 0.72f

        points.forEachIndexed { index, point ->
            val barHeight = (point.value / maxValue) * size.height
            // A small stub for empty days keeps the timeline readable.
            val stub = 2.dp.toPx()
            val height = if (point.value <= 0f) stub else barHeight
            val color = when (point.state) {
                AdherenceCalculator.DayState.GOOD -> goodColor
                AdherenceCalculator.DayState.OVER -> overColor
                AdherenceCalculator.DayState.EMPTY -> emptyColor
            }
            drawRect(
                color = color,
                topLeft = Offset(
                    x = index * slotWidth + (slotWidth - barWidth) / 2f,
                    y = size.height - height,
                ),
                size = Size(barWidth, height),
            )
        }

        targetLine?.let { target ->
            val y = size.height - (target / maxValue) * size.height
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
    }
}
