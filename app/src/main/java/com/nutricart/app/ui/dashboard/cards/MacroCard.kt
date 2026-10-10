package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.domain.logic.NutrientTargets
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.ember.Bar
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.WholeWordsAutoSize

/** Next to a limit's name: nothing, "near limit" (just past it, up to 120%) or "over". */
private enum class LimitTag { None, Near, Over }

/**
 * Macros and limits: protein / fat / carbs as three columns (dot, value, bar, "of 140 g"), then a
 * hairline and four limit rows (fiber to reach; sugars, salt and saturated fat to stay under) with
 * thin bars. Meaning never rides on hue alone: a limit past its line says so in words ("near
 * limit", "over"). One TalkBack node. [first] fills the bars on the first open of the day.
 */
@Composable
internal fun MacroCard(state: DashboardUiState, modifier: Modifier = Modifier, first: Boolean = false) {
    val targets = state.targets ?: return
    val c = Ember.colors
    val stacked = LocalDensity.current.fontScale >= 1.5f
    EmberCard(modifier, mergeDescendants = true) {
        CardHead(stringResource(R.string.macros_title), meta = stringResource(R.string.macros_meta))
        val macros = listOf(
            Macro(stringResource(R.string.summary_protein), c.protein, state.eatenProteinG, targets.proteinG),
            Macro(stringResource(R.string.summary_fat), c.fat, state.eatenFatG, targets.fatG),
            Macro(stringResource(R.string.summary_carbs), c.carbs, state.eatenCarbsG, targets.carbsG),
        )
        if (stacked) {
            // Twice the text size cannot share a row three ways: one macro per line, bar under it.
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                macros.forEachIndexed { i, m -> MacroColumn(m, if (first) 420 + 80 * i else null, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                macros.forEachIndexed { i, m -> MacroColumn(m, if (first) 420 + 80 * i else null, Modifier.weight(1f)) }
            }
        }
        Column(
            Modifier
                .padding(top = 16.dp)
                .topHairline(c.sep, 0.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Fiber is a target to reach (no tag); the other three are limits to stay under.
            LimitRow(R.string.nutrient_fiber, state.eatenFiberG, state.fiberTargetG, c.carbs, LimitTag.None, first, 0)
            LimitRow(R.string.nutrient_sugars, state.eatenSugarsG, state.sugarLimitG, c.fat, tagOf(state.eatenSugarsG, state.sugarLimitG), first, 1)
            LimitRow(R.string.nutrient_salt, state.eatenSaltG, state.saltLimitG, c.weight, tagOf(state.eatenSaltG, state.saltLimitG), first, 2)
            LimitRow(R.string.nutrient_sat_fat, state.eatenSatFatG, state.satFatLimitG, c.protein, tagOf(state.eatenSatFatG, state.satFatLimitG), first, 3)
        }
    }
}

private class Macro(val label: String, val color: Color, val eaten: Int, val target: Int)

/** The old colours as words: amber was "a little past the limit", red was "well past it". */
private fun tagOf(eatenG: Double, limitG: Int): LimitTag = when (NutrientTargets.limitState(eatenG, limitG.toDouble())) {
    NutrientTargets.State.WARN -> LimitTag.Near
    NutrientTargets.State.OVER -> LimitTag.Over
    else -> LimitTag.None
}

@Composable
private fun MacroColumn(m: Macro, fillDelay: Int?, modifier: Modifier) {
    val c = Ember.colors
    val t = Ember.type
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(m.color, EmberShapes.circle))
            // A third of the card is narrow for a long name at 1.3x text (the Ukrainian "Carbs"): the label
            // shrinks a little (to 11 sp at most) rather than lose its last letters. The line height
            // stays, so the three values below still line up.
            BasicText(
                m.label,
                style = t.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2),
                autoSize = WholeWordsAutoSize(t.footnote.fontSize, min = 11.sp),
            )
        }
        ValueText(
            stringResource(R.string.grams_value, m.eaten),
            t.statSmall,
            Modifier.padding(top = 3.dp),
            unitSize = 13.sp,
        )
        Bar(
            fraction = if (m.target > 0) m.eaten / m.target.toFloat() else 0f,
            color = m.color,
            contentDescription = null,
            modifier = Modifier.padding(top = 8.dp),
            fillDelayMillis = fillDelay,
        )
        BasicText(
            stringResource(R.string.of_target_g, m.target),
            Modifier.padding(top = 6.dp),
            style = t.footnote.copy(color = c.label2),
            maxLines = 1,
        )
    }
}

@Composable
private fun LimitRow(labelRes: Int, eatenG: Double, limitG: Int, color: Color, tag: LimitTag, first: Boolean, index: Int) {
    val c = Ember.colors
    val t = Ember.type
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(labelRes), style = t.footnote.copy(fontSize = 14.sp, color = c.label))
                if (tag != LimitTag.None) Tag(tag)
            }
            ValueText(
                stringResource(R.string.nutrient_vs_limit, eatenG, limitG),
                t.rowNumber.copy(fontSize = 14.sp),
                unitSize = 13.sp,
                unitWeight = FontWeight.Medium,
                firstNumberOnly = true,
            )
        }
        Bar(
            fraction = if (limitG > 0) (eatenG / limitG).toFloat() else 0f,
            color = color,
            contentDescription = null,
            modifier = Modifier.padding(top = 6.dp),
            height = 4.dp,
            fillDelayMillis = if (first) 660 + 60 * index else null,
        )
    }
}

/** "near limit" on a soft amber, "over" in ink: small capsules after a limit's name. */
@Composable
private fun Tag(tag: LimitTag) {
    val c = Ember.colors
    val (bg, fg, text) = when (tag) {
        LimitTag.Near -> Triple(c.fat.copy(alpha = .20f), c.fatInk, stringResource(R.string.limit_near))
        else -> Triple(c.ink, c.onInk, stringResource(R.string.limit_over))
    }
    BasicText(
        text,
        Modifier
            .background(bg, EmberShapes.capsule)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = Ember.type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = fg),
        maxLines = 1,
    )
}
