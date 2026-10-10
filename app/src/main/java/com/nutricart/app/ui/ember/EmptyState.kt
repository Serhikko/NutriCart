package com.nutricart.app.ui.ember

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

// What a screen or a section shows when there is nothing in it yet (calm, centred, one way forward),
// and the inline message card for information, warnings and errors.

/**
 * A calm empty state: a 44 dp [icon] in the decoration grey (or custom [art], e.g. the plan's empty
 * ring with an Ember glyph; decorative either way), a [title] (17 sp SemiBold, a heading), an optional
 * [body] footnote and one [action] (usually an Ink button). It has no background: on a page it sits
 * in an EmberCard, in a list or a sheet it stands alone. The parts fade up as they appear.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: EmberIcons? = null,
    art: (@Composable () -> Unit)? = null,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Ember.colors
    val t = Ember.type
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var step = 0
        when {
            art != null -> Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .clearAndSetSemantics {}
                    .emberEntrance(step++, EntranceKind.FadeUp, first = false),
            ) { art() }
            icon != null -> EmberIcon(
                icon, null,
                Modifier.padding(bottom = 8.dp).emberEntrance(step++, EntranceKind.FadeUp, first = false),
                size = 44.dp, tint = c.label3, strokeWidth = 1.6f,
            )
        }
        ControlText(
            title,
            Modifier
                .widthIn(max = 340.dp)
                .semantics { heading() }
                .emberEntrance(step++, EntranceKind.FadeUp, first = false),
            style = t.headline.copy(lineBreak = LineBreak.Heading),
            color = c.label,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            ControlText(
                body,
                Modifier.widthIn(max = 320.dp).emberEntrance(step++, EntranceKind.FadeUp, first = false),
                style = t.footnote.copy(lineHeight = 1.4.em, lineBreak = LineBreak.Heading),
                color = c.label2,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = 14.dp).emberEntrance(step, EntranceKind.FadeUp, first = false)) { action() }
        }
    }
}

/** Info = a neutral note; Warning = something to look at (`fatInk` glyph); Error = it failed (`danger`, announced). */
enum class NoticeTone { Info, Warning, Error }

/**
 * An inline message: an Info / Warning / Error glyph (or [icon], e.g. Heart for the Health Connect
 * banner), a [title] (15 sp SemiBold; `danger` for errors), an optional [detail] and an [action] row
 * (buttons wrap). On the stage it is a card (radius 16, the card shadow); [nested] = the quieter
 * `surface2` block inside a card or a sheet. Title and detail read as one TalkBack stop; errors are a
 * polite live region; the action stays its own button.
 */
@Composable
fun Notice(
    tone: NoticeTone,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
    nested: Boolean = false,
    icon: EmberIcons? = null,
) {
    val c = Ember.colors
    val t = Ember.type
    val glyph = icon ?: when (tone) {
        NoticeTone.Info -> EmberIcons.Info
        NoticeTone.Warning -> EmberIcons.Warning
        NoticeTone.Error -> EmberIcons.Warning
    }
    val glyphColor = when (tone) {
        NoticeTone.Info -> c.label2
        NoticeTone.Warning -> c.fatInk
        NoticeTone.Error -> c.danger
    }
    val shape = EmberShapes.nestedSmall
    Row(
        modifier
            .fillMaxWidth()
            .then(if (nested) Modifier else Modifier.emberShadow(Elevation.Card, shape, c.isDark))
            .background(if (nested) c.surface2 else c.surface, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmberIcon(glyph, null, size = 20.dp, tint = glyphColor)
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    if (tone == NoticeTone.Error) liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ControlText(title, style = t.subhead, color = if (tone == NoticeTone.Error) c.danger else c.label)
                if (detail != null) ControlText(detail, style = t.footnote, color = c.label2)
            }
            if (action != null) {
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { action() }
            }
        }
    }
}
