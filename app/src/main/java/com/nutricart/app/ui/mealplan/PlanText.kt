package com.nutricart.app.ui.mealplan

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nutricart.app.R
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.rememberIntegerFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Small pieces the meal plan, the recipe, the fridge and the shopping list share: localized dates,
// the week as an eyebrow, a hint split into a title and its explanation, and the aisle head.

/** The locale's own pattern for a date [skeleton] ("EEEEdMMMM" → "EEEE, MMMM d" in English, "EEEE, d MMMM" in Ukrainian). */
internal fun bestPattern(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** First letter up, the way the locale does it ("субота" → "Субота"). */
internal fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

/** The locale the screen is drawn in (it follows the system language). */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * The plan's week as an eyebrow: "October 10 – 16" / "10 – 16 жовтня" inside one month,
 * "October 28 – November 3" across two. The day number alone stands on the side where the locale
 * puts the day, so the month is written once.
 */
@Composable
internal fun rememberWeekLabel(from: LocalDate, to: LocalDate): String {
    val locale = currentLocale()
    val (a, b) = remember(from, to, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMM")
        val full = DateTimeFormatter.ofPattern(pattern, locale)
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val month = pattern.indexOfFirst { it == 'M' || it == 'L' }
        val dayFirst = month < 0 || pattern.indexOf('d') < month
        when {
            from.month != to.month || from.year != to.year -> from.format(full) to to.format(full)
            dayFirst -> from.format(dayOnly) to to.format(full)
            else -> from.format(full) to to.format(dayOnly)
        }
    }
    return stringResource(R.string.date_range, a, b)
}

/** "Saturday, October 10" / "Субота, 10 жовтня". */
internal fun longDate(date: LocalDate, locale: Locale): String =
    date.format(bestPattern(locale, "EEEEdMMMM")).capitalized(locale)

/** "Sat, Oct 10" / "Сб, 10 жовт.". */
internal fun shortDate(date: LocalDate, locale: Locale): String =
    date.format(bestPattern(locale, "EEEdMMM")).capitalized(locale)

/**
 * An existing hint written as one sentence pair ("No plan yet. The generator builds…", "Generate a
 * meal plan first — the shopping list is built from it.") as a title and its explanation, so an
 * empty state can show the first part as its heading. The text stays one string for translators;
 * when it has no break, all of it is the title.
 */
internal fun splitHint(text: String, locale: Locale): Pair<String, String?> {
    val stop = text.indexOf(". ").takeIf { it > 0 }
    val dash = text.indexOf(" — ").takeIf { it > 0 }
    val cut = listOfNotNull(stop, dash).minOrNull() ?: return text to null
    val lead = text.substring(0, cut).trimEnd('.')
    val rest = text.substring(if (cut == stop) cut + 2 else cut + 3).trim()
    return if (rest.isEmpty()) lead to null else lead to rest.capitalized(locale)
}

/**
 * [spec], or a jump under "Remove animations": for the transitions that need a finite spec
 * (AnimatedVisibility, AnimatedContent, animateContentSize). Compose would finish them at once on a
 * device anyway; gating here keeps it so wherever the animator scale is not applied.
 */
@Composable
@ReadOnlyComposable
internal fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (Ember.motion.reduced) snap() else spec

/** Whole grams with the locale's thousands separator ("1,250" / "1 250"). */
@Composable
internal fun rememberGrams(grams: Int): String = rememberIntegerFormat().format(grams.toLong())

/**
 * The head of one aisle (or any group of rows) on a page: the label as a heading and an optional
 * count at the end, 13 sp SemiBold label 2, 16 dp in from the card's edge like an inset group's head.
 * It sits [top] (10 dp) under the item above it, which with the page's 12 dp gap is the web's 22.
 */
@Composable
internal fun GroupHead(label: String, modifier: Modifier = Modifier, count: Int? = null, top: Dp = 10.dp) {
    val c = Ember.colors
    val style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ControlText(label, Modifier.weight(1f).semantics { heading() }, style = style, color = c.label2)
        if (count != null) {
            ControlText(
                rememberIntegerFormat().format(count.toLong()),
                style = style.copy(fontWeight = FontWeight.Medium),
                color = c.label2,
            )
        }
    }
}

/**
 * Lets a row of 48 dp round tools start where the text above it starts: each tool draws 36 dp inside
 * a 48 dp touch area, so the row is pulled [by] (6 dp) towards the start and widened by as much.
 */
internal fun Modifier.bleedStart(by: Dp): Modifier = layout { measurable, constraints ->
    val px = by.roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.maxWidth + px else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth, maxWidth = wider))
    val width = (placeable.width - px).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.placeRelative(-px, 0) }
}
