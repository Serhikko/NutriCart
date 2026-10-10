package com.nutricart.app.ui.ember

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Text fields as the web draws them: the label above the well (never a floating label), a soft
// `fill2` well with a hairline rim that turns white with a blue focus ring while typing, and a red
// ring plus a line of text on error. Also the search capsule with its ink "Search" pill, and the date
// row that opens the Material date picker in an Ember sheet.

/** The well's rim at rest, focused, and in error. */
private val RimWidth = 1.dp
private val RingWidth = 2.dp

/**
 * A text field: [label] above the well (13 sp SemiBold label2; never a floating label), the `fill2`
 * well r14 48 dp with a hairline rim, 17 sp text, a [suffix] unit and an optional [trailing] control
 * (a show/hide button) inside it; focus = the well turns `surface` with a 2 dp `focus` ring; error =
 * a 2 dp `danger` ring plus [errorText] under it ([supportingText] otherwise). The label, the text and
 * the error are one TalkBack node; tapping the label starts typing, like an HTML label.
 */
@Composable
fun EmberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val well by animateColorAsState(
        if (focused && enabled) c.surface else c.fill2, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "well",
    )
    val ring by animateColorAsState(
        when {
            isError -> c.danger
            focused && enabled -> c.focus
            else -> c.sep
        },
        emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "ring",
    )
    val strong = isError || (focused && enabled)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (isError) error(errorText ?: label) },
        enabled = enabled,
        textStyle = t.body.copy(color = c.label),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        interactionSource = source,
        cursorBrush = SolidColor(c.tint),
        decorationBox = { inner ->
            Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }) {
                FieldLabel(label)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(EmberShapes.field)
                        .background(well)
                        .border(if (strong) RingWidth else RimWidth, ring, EmberShapes.field)
                        .padding(start = 14.dp, end = if (trailing != null) 2.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            ControlText(placeholder, style = t.body, color = c.label2, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                    if (suffix != null) {
                        ControlText(
                            suffix,
                            Modifier.padding(top = if (singleLine) 0.dp else 14.dp),
                            style = t.subhead.copy(fontWeight = FontWeight.Normal),
                            color = c.label2,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (trailing != null) {
                        Box(Modifier.align(if (singleLine) Alignment.CenterVertically else Alignment.Top)) { trailing() }
                    }
                }
                when {
                    isError && errorText != null -> FieldNote(errorText, c.danger)
                    supportingText != null -> FieldNote(supportingText, c.label2)
                }
            }
        },
    )
}

/** The label above a well: 13 sp SemiBold label2, 4 dp in, 6 dp of air below. */
@Composable
private fun FieldLabel(text: String) = ControlText(
    text,
    Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
    style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em),
    color = Ember.colors.label2,
)

/** The line under a well: the error in `danger`, or a supporting note in label2. */
@Composable
private fun FieldNote(text: String, color: Color) = ControlText(
    text,
    Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
    style = Ember.type.footnote,
    color = color,
)

/**
 * The search capsule: `surface` with the card shadow and a hairline rim, a search glyph, the field
 * (17 sp, IME Search runs [onSearch]) and an ink "Search" pill ([searchLabel]) at the end. Focus = a
 * 2 dp `focus` ring round the capsule. [placeholder] is also the field's accessible name.
 */
@Composable
fun EmberSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    searchLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .emberShadow(Elevation.Card, EmberShapes.capsule, c.isDark)
            .clip(EmberShapes.capsule)
            .background(c.surface)
            .border(if (focused) RingWidth else .5.dp, if (focused) c.focus else c.sep, EmberShapes.capsule)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmberIcon(EmberIcons.Search, null, size = 20.dp, tint = c.label2)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            textStyle = t.body.copy(color = c.label),
            cursorBrush = SolidColor(c.tint),
            interactionSource = source,
            // The full height of the capsule, so the field is 48 dp to the finger.
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    // The placeholder stays in the field's node, so it is also its name.
                    if (query.isEmpty()) {
                        ControlText(placeholder, style = t.body, color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        SearchPill(searchLabel, onSearch)
    }
}

/** The ink "Search" pill inside the capsule: 40 dp drawn, 48 dp to the finger. */
@Composable
private fun SearchPill(text: String, onClick: () -> Unit) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .pressScale(source, .96f)
            .heightIn(min = 40.dp)
            .clip(EmberShapes.capsule)
            .background(c.ink)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ControlText(text, style = Ember.type.subhead, color = c.onInk, maxLines = 1, softWrap = false)
    }
}

/**
 * A date row: [label], the LONG date (or [placeholder] in `tint`) and a chevron, for an InsetGroup.
 * Tapping opens an EmberSheet with the Material date picker (years in [yearRange]) and a single OK
 * button; dragging it down or back dismisses it, as an outside tap dismissed the old dialog. The
 * picker keeps its pencil: a birth date decades back is quicker to type than to page to, and much
 * easier with TalkBack. The open flag survives rotation. At large font sizes the date moves under
 * the label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onPick: (LocalDate) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    yearRange: IntRange = 1920..LocalDate.now().year,
) {
    val c = Ember.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val text = date?.format(formatter) ?: placeholder
    val color = if (date != null) c.label2 else c.tint
    if (LocalDensity.current.fontScale >= 1.5f) {
        ListRow(title = label, modifier = modifier, subtitle = text, onClick = { open = true }, chevron = true)
    } else {
        ListRow(
            title = label,
            modifier = modifier,
            trailing = { ControlText(text, style = Ember.type.callout, color = color, maxLines = 1, softWrap = false) },
            onClick = { open = true },
            chevron = true,
        )
    }
    if (open) {
        // Seeded with the chosen date, so reopening continues from it. The Material picker works in
        // UTC, so the conversion back is in UTC too (otherwise the day can shift in some time zones).
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = yearRange,
        )
        EmberSheet(onDismissRequest = { open = false }, paneTitle = label) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The calendar needs 360 dp (seven 48 dp days plus its own padding); on a phone it
                // borrows the sheet's side padding rather than squeezing the days.
                val width = maxOf(maxWidth, min(360.dp, maxWidth + 40.dp))
                // The picker's day grid is fixed at 48 dp cells: past 1.4× its numbers would collide
                // and its month arrows get squeezed under 48 dp (a long Ukrainian month at 1.5×), so
                // its own text stops growing there (the row, the sheet and the OK button keep the
                // full scale).
                val outer = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(outer.density, outer.fontScale.coerceAtMost(1.4f)),
                ) {
                    DatePicker(
                        state = state,
                        modifier = Modifier.requiredWidth(width),
                        colors = DatePickerDefaults.colors(
                            containerColor = Color.Transparent,
                            titleContentColor = c.label2,
                            headlineContentColor = c.label,
                            weekdayContentColor = c.label2,
                            subheadContentColor = c.label2,
                            navigationContentColor = c.label,
                            yearContentColor = c.label,
                            currentYearContentColor = c.tint,
                            selectedYearContentColor = c.onInk,
                            selectedYearContainerColor = c.ink,
                            dayContentColor = c.label,
                            selectedDayContentColor = c.onInk,
                            selectedDayContainerColor = c.ink,
                            todayContentColor = c.tint,
                            todayDateBorderColor = c.tint,
                            dividerColor = c.sep,
                            // The pencil's typed entry: Ember's field colours (the blue focus ring,
                            // the tint cursor, a quiet rim at rest) instead of Material's ink outline.
                            dateTextFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = c.label,
                                unfocusedTextColor = c.label,
                                focusedContainerColor = c.surface,
                                unfocusedContainerColor = c.fill2,
                                cursorColor = c.tint,
                                focusedBorderColor = c.focus,
                                unfocusedBorderColor = c.sepStrong,
                                focusedLabelColor = c.label2,
                                unfocusedLabelColor = c.label2,
                                focusedPlaceholderColor = c.label2,
                                unfocusedPlaceholderColor = c.label2,
                                focusedSupportingTextColor = c.label2,
                                unfocusedSupportingTextColor = c.label2,
                                errorBorderColor = c.danger,
                                errorLabelColor = c.danger,
                                errorSupportingTextColor = c.danger,
                                errorCursorColor = c.danger,
                                errorContainerColor = c.surface,
                            ),
                        ),
                    )
                }
            }
            EmberButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                },
                modifier = Modifier.padding(top = 8.dp),
                size = ButtonSize.Lg,
            )
        }
    }
}
