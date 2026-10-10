package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutricart.app.ui.ember.Ember

// Small pieces shared by the quick-add sheet and the sheets it opens (workout, weight), and the
// cooked-portions sheet. SheetCloser and SheetButtonPair, which Settings uses too, are in ui/ember/Sheet.kt.

/**
 * The small label above a group of controls in a sheet (the web's `.f-l`): 13 sp SemiBold, label2.
 * Only drawn: the control below carries the same words as its name (a chip group's label, a
 * stepper's field name), so TalkBack does not read them twice.
 */
@Composable
internal fun SheetFieldLabel(text: String, modifier: Modifier = Modifier) {
    val color = Ember.colors.label2
    BasicText(
        text,
        style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = { color },
        modifier = modifier
            .clearAndSetSemantics { }
            .padding(start = 4.dp, bottom = 8.dp),
    )
}
