package com.nutricart.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import kotlin.math.max

/**
 * One macro as "eaten / target" with a smoothly animated progress bar.
 * [valueColor] colors the number pair — the dashboard uses it for the
 * "green when in range" feedback (green/amber/red by nutrient state).
 */
@Composable
fun MacroBar(
    label: String,
    eatenG: Int,
    targetG: Int,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    val fraction by animateFloatAsState(
        targetValue = (eatenG / max(1, targetG).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "macro-$label",
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.macro_pair, eatenG, targetG),
                style = MaterialTheme.typography.labelLarge,
                color = if (valueColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    valueColor
                },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Track + animated fill, both clipped to pill shape.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
