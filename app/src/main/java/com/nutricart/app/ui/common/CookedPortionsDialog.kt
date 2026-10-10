package com.nutricart.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.Digits
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.SheetButtonPair
import com.nutricart.app.ui.ember.rememberSheetCloser

/**
 * "How many portions did you cook?" — one number, defaulting to one.
 *
 * Batch cooking plans the SAME pot for two or three days, so a single tap has
 * to be able to say "I cooked all three". Asking beats guessing from the plan:
 * a hidden multiplier is exactly the kind of invisible arithmetic that makes a
 * fridge quietly wrong.
 *
 * Shared between the recipe screen and the plan row so both deduct by the same
 * rule and there is only one sheet to explain. A sheet, not a dialog: the pot
 * glyph, the question, a big − N + and the two buttons, Cancel beside the
 * confirm (the confirm does something to the fridge, so it is never the only
 * way out). Confirming slides the sheet away first, then deducts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookedPortionsDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var portions by rememberSaveable { mutableIntStateOf(1) }
    val closer = rememberSheetCloser()
    val c = Ember.colors
    val title = stringResource(R.string.fridge_cooked_title)

    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(c.fill2, EmberShapes.nestedSmall),
                contentAlignment = Alignment.Center,
            ) {
                EmberIcon(EmberIcons.Pot, null, size = 28.dp, brush = EmberBrushes.emberIcon(c), strokeWidth = 2f)
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                title,
                style = Ember.type.title2.copy(textAlign = TextAlign.Center),
                color = { c.label },
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                stringResource(R.string.fridge_cooked_hint),
                style = Ember.type.footnote.copy(textAlign = TextAlign.Center),
                color = { c.label2 },
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }

        // The stepper: 52 dp buttons named "Less" / "More" (the old glyph-only "−" "+"), the count
        // in big tabular digits that hand off as it changes, the unit under it.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmberIconButton(
                EmberIcons.Minus,
                stringResource(R.string.amount_less),
                onClick = { if (portions > 1) portions-- },
                size = 52.dp,
                enabled = portions > 1,
            )
            val unit = pluralStringResource(R.plurals.quickadd_portions, portions)
            Column(
                Modifier
                    .widthIn(min = 72.dp)
                    // One stop that reads "3 portions" and speaks again after each tap.
                    .clearAndSetSemantics {
                        contentDescription = "$portions $unit"
                        liveRegion = LiveRegionMode.Polite
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Digits(
                    portions.toLong(),
                    Ember.type.display.copy(fontSize = 56.sp, color = c.label),
                )
                BasicText(
                    unit,
                    style = Ember.type.subhead.copy(textAlign = TextAlign.Center),
                    color = { c.label2 },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            EmberIconButton(
                EmberIcons.Plus,
                stringResource(R.string.amount_more),
                onClick = { if (portions < MAX_PORTIONS) portions++ },
                size = 52.dp,
                enabled = portions < MAX_PORTIONS,
            )
        }

        val cancel = stringResource(R.string.cancel)
        val confirm = stringResource(R.string.fridge_cooked_action)
        val onCancel = { closer.close(onDismiss) }
        val onCooked = { closer.close { onConfirm(portions) } }
        // Cancel beside the confirm (the confirm changes the fridge, so it is never the only way out).
        SheetButtonPair(
            cancel = { EmberButton(cancel, onCancel, variant = ButtonVariant.Fill, size = ButtonSize.Lg) },
            confirm = { EmberButton(confirm, onCooked, size = ButtonSize.Lg, icon = EmberIcons.Pot) },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A week has seven days; nobody cooks more portions of one dish at once. */
private const val MAX_PORTIONS = 7
