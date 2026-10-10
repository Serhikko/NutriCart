package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.TodayMenuItem
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit

/** The card's side padding; rows reach the card's edges so their pressed fill does too. */
private val Side = 18.dp

/**
 * Today's planned meals, one tap away from the recipe: the meal's squircle, its name over the
 * recipe (one line; up to four from 1.5x text, with the kcal under it), the kcal and a chevron.
 * Each row is one button that darkens while pressed.
 */
@Composable
internal fun TodayMenuCard(
    menu: List<TodayMenuItem>,
    onOpenRecipe: (Long, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmberCard(modifier, padding = PaddingValues(top = Side, bottom = 6.dp)) {
        CardHead(
            stringResource(R.string.today_menu_title),
            Modifier.padding(horizontal = Side),
            icon = EmberIcons.Plan,
            metric = Metric.Kcal,
            meta = stringResource(R.string.today_menu_meta),
        )
        menu.forEachIndexed { i, item -> MenuRow(item, divided = i > 0, onClick = { onOpenRecipe(item.recipeId, item.portionFactor) }) }
    }
}

@Composable
private fun MenuRow(item: TodayMenuItem, divided: Boolean, onClick: () -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            // The hairline starts under the text, past the 34 dp squircle.
            .then(if (divided) Modifier.topHairline(c.sep, Side + 46.dp, Side) else Modifier)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Side, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // From 1.5x text the kcal moves under the name and the name may wrap: beside a fixed number
        // the name was cut to its first few letters, and the dish is the point of the row.
        val large = LocalDensity.current.fontScale >= 1.5f
        val kcal: @Composable (Modifier) -> Unit = { modifier ->
            NumberWithUnit(item.kcal.toLong(), stringResource(R.string.kcal_unit), t.rowNumber, modifier, unitSize = 12.sp)
        }
        MealTile(item.slot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                mealSlotLabel(item.slot),
                style = t.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                item.name,
                style = t.callout.copy(color = c.label),
                maxLines = if (large) 4 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (large) kcal(Modifier.padding(top = 3.dp))
        }
        if (!large) kcal(Modifier)
        EmberIcon(EmberIcons.Right, null, Modifier.offset(x = 4.dp), size = 20.dp, tint = c.label3)
    }
}
