package com.nutricart.app.ui.fridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.mealplan.motionSpec

/**
 * The optional assistant. Without a key this is a single line of text pointing
 * at Settings — no request, no error, and everything else on the screen works
 * exactly the same.
 *
 * The answer is rendered as it came and is never parsed: nothing the model
 * writes can reach the database or a number the app shows.
 *
 * An Ember card headed "Assistant" with the Ember sparkle. With a key it offers "Ask what to cook"
 * (the spinner and "Asking…" while a request runs, with placeholder lines where the answer will go);
 * the answer unfolds in the card and its paragraphs fade up in turn, with the disclaimer and the
 * token count under a hairline; Cancel in the head puts it away. A failure is an inline error with
 * the button under it, so trying again is one tap.
 */
@Composable
fun FridgeAiBlock(
    state: AiUiState,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val t = Ember.type
    val result = state.result
    val text = t.callout.copy(fontSize = 15.sp, lineHeight = 1.45.em, letterSpacing = (-0.01).em)
    EmberCard(modifier) {
        CardHead(
            label = stringResource(R.string.ai_card_title),
            icon = EmberIcons.Sparkle,
            iconBrush = EmberBrushes.emberIcon(c),
            action = if (state.hasKey && result != null) {
                { PlainLink(text = stringResource(R.string.cancel), onClick = onDismiss) }
            } else {
                null
            },
        )
        if (!state.hasKey) {
            ControlText(stringResource(R.string.ai_no_key_hint), style = text, color = c.label2)
            PlainLink(
                text = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@EmberCard
        }

        when (result) {
            is AiResult.Ok -> Answer(result, text)
            null -> {
                if (state.loading) AskingLines()
                AskButton(state.loading, canAsk, onAsk)
            }
            else -> {
                Notice(NoticeTone.Error, stringResource(errorMessage(result)), nested = true)
                Spacer(Modifier.height(12.dp))
                AskButton(state.loading, canAsk, onAsk)
            }
        }
    }
}

/** "Ask what to cook" (Fill): never on an empty fridge and never twice at once; the spinner while asking. */
@Composable
private fun AskButton(loading: Boolean, canAsk: Boolean, onAsk: () -> Unit) {
    EmberButton(
        text = stringResource(if (loading) R.string.ai_asking else R.string.ai_ask_action),
        onClick = onAsk,
        variant = ButtonVariant.Fill,
        icon = EmberIcons.Sparkle,
        // Never spend the user's money on an empty fridge, and never twice at the same time.
        enabled = canAsk,
        loading = loading,
    )
}

/** Three lines that pulse where the answer will appear. */
@Composable
private fun AskingLines() {
    Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SkeletonLine(290.dp, height = 12.dp)
        SkeletonLine(250.dp, height = 12.dp)
        SkeletonLine(170.dp, height = 12.dp)
    }
}

/**
 * The answer as it came (never parsed). The card unfolds to hold it and its paragraphs fade up one
 * group after another (no typewriter); the disclaimer and what the question cost sit under a hairline.
 */
@Composable
private fun Answer(result: AiResult.Ok, style: TextStyle) {
    val c = Ember.colors
    val t = Ember.type
    val reduced = Ember.motion.reduced
    val shown = remember(result) { MutableTransitionState(reduced) }
    shown.targetState = true
    AnimatedVisibility(
        visibleState = shown,
        enter = expandVertically(motionSpec(EmberSprings.smooth())) + fadeIn(motionSpec(tween(200))),
    ) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            val paragraphs = result.text.split("\n").filter { it.isNotBlank() }.ifEmpty { listOf(result.text) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                paragraphs.forEachIndexed { i, p ->
                    FadeUp(i, result) { ControlText(p.trim(), style = style, color = c.label) }
                }
            }
            if (result.truncated) {
                ControlText(
                    stringResource(R.string.ai_truncated),
                    Modifier.padding(top = 6.dp),
                    style = t.footnote,
                    color = c.label2,
                )
            }
            Spacer(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(.5.dp)
                    .background(c.sep),
            )
            // The permanent line under every answer: the app cannot check an AI dish against the
            // allergy list, because ingredients carry no allergen data.
            ControlText(stringResource(R.string.ai_disclaimer), Modifier.padding(top = 12.dp), style = t.footnote, color = c.label2)
            // What this question just cost, on the user's own key.
            ControlText(
                stringResource(R.string.ai_tokens, result.inputTokens, result.outputTokens),
                Modifier.padding(top = 4.dp),
                style = t.footnote.copy(fontFeatureSettings = "tnum"),
                color = c.label2,
            )
        }
    }
}

/** One paragraph of the answer rising 8 dp into place, 120 ms after the one before it. */
@Composable
private fun FadeUp(index: Int, key: Any, content: @Composable () -> Unit) {
    val reduced = Ember.motion.reduced
    val p = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (p.value < 1f) {
            motionDelay(160L + 120L * index.coerceAtMost(6))
            p.animateTo(1f, tween(420, easing = EmberEasing.Out))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = 8.dp.toPx() * (1f - p.value)
        },
    ) { content() }
}

/** Each failure gets its own words; "something went wrong" is the last resort. */
private fun errorMessage(result: AiResult): Int = when (result) {
    AiResult.BadKey -> R.string.ai_error_bad_key
    AiResult.Offline -> R.string.ai_error_offline
    AiResult.TooSlow -> R.string.ai_error_slow
    AiResult.Busy -> R.string.ai_error_busy
    AiResult.Refused -> R.string.ai_error_refused
    AiResult.Empty -> R.string.ai_error_empty
    AiResult.NoKey -> R.string.ai_no_key_hint
    else -> R.string.ai_error_failed
}
