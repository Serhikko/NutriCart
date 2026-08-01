package com.nutricart.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nutricart.app.domain.logic.WeightTrendCalculator

/**
 * A minimalist line chart of weight over time: measurement dots connected by
 * a line, plus the dashed least-squares trend line. Pure Canvas — every draw
 * call is a straight line or a circle, nothing hidden.
 * [points] must be per-day deduped and sorted by day (the ViewModel does it).
 */
@Composable
fun WeightChart(
    points: List<Pair<Long, Double>>,
    trend: WeightTrendCalculator.Trend?,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trendColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val minDay = points.first().first
        val maxDay = points.last().first
        // Half a kilo of breathing room above and below the data.
        val minW = points.minOf { it.second } - 0.5
        val maxW = points.maxOf { it.second } + 0.5

        fun xOf(day: Long): Float =
            ((day - minDay).toFloat() / (maxDay - minDay).toFloat()) * size.width

        fun yOf(weight: Double): Float =
            size.height - (((weight - minW) / (maxW - minW)).toFloat() * size.height)

        // Three faint horizontal guide lines.
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        // Dashed trend line underneath the data.
        if (trend != null) {
            drawLine(
                color = trendColor,
                start = Offset(xOf(minDay), yOf(trend.weightAt(minDay))),
                end = Offset(xOf(maxDay), yOf(trend.weightAt(maxDay))),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
            )
        }

        // The measurement line.
        val path = Path()
        points.forEachIndexed { index, (day, weight) ->
            if (index == 0) path.moveTo(xOf(day), yOf(weight))
            else path.lineTo(xOf(day), yOf(weight))
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // A dot per measurement.
        points.forEach { (day, weight) ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(xOf(day), yOf(weight)))
        }
    }
}
