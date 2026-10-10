package com.nutricart.app.ui.dashboard.cards

import android.text.format.DateFormat
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.nutricart.app.R
import com.nutricart.app.ui.ember.Ember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Small pieces the Today and Statistics bodies share: localized dates for eyebrows, chart and
// marker labels, numbers with their units drawn smaller, and the hairline between rows in a card.

/** The locale's own pattern for a date [skeleton] ("EEEEdMMMM" → "EEEE, MMMM d" in English, "EEEE, d MMMM" in Ukrainian). */
internal fun bestPattern(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** First letter up, the way the locale does it ("субота" → "Субота"). */
internal fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

/** "Saturday, October 10" / "Субота, 10 жовтня": the day's full name, for the Today eyebrow and TalkBack. */
@Composable
internal fun rememberLongDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) { date.format(bestPattern(locale, "EEEEdMMMM")).capitalized(locale) }
}

/** "Sat" / "Сб": a short weekday label (never split out of a longer formatted date). */
internal fun shortWeekday(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.').capitalized(locale)

/**
 * The period a statistics range covers, as an eyebrow: "October 4 – 10" / "4 – 10 жовтня" inside one
 * month, "September 11 – October 10" across two. The day number alone stands on the side where the
 * locale puts the day, so the month is written once.
 */
@Composable
internal fun rememberPeriodLabel(from: LocalDate, to: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
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

/** "16:52" or "4:52 PM", as the phone's clock is set. */
@Composable
internal fun rememberClockTime(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    return remember(epochMillis, locale) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(bestPattern(locale, skeleton))
    }
}

/** True when this character belongs to a number: a digit, a sign before one, or a separator between two. */
private fun String.isNumberAt(i: Int): Boolean {
    val ch = this[i]
    if (ch.isDigit()) return true
    val digitBefore = i > 0 && this[i - 1].isDigit()
    val digitAfter = i + 1 < length && this[i + 1].isDigit()
    if (digitBefore && digitAfter && ch in ".,'    ") return true
    return digitAfter && ch in "+-−" && (i == 0 || !this[i - 1].isLetterOrDigit())
}

/**
 * A value that an existing string already formats with its unit ("7h 12m", "48 min", "78.4 kg",
 * "112 / 140 g"): the numbers in [numberStyle], the words between them as units (56% of the size, or
 * [unitSize], in label2), so units keep coming from the translations. With [firstNumberOnly] only the
 * first number is big ("112" of "112 / 140 g"). One line, never wrapped.
 */
@Composable
internal fun ValueText(
    text: String,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    unitSize: TextUnit = TextUnit.Unspecified,
    unitWeight: FontWeight = FontWeight.SemiBold,
    firstNumberOnly: Boolean = false,
) {
    val c = Ember.colors
    val size = if (unitSize.isSpecified) unitSize else numberStyle.fontSize * Ember.type.unitRatio
    val ink = if (color != Color.Unspecified) color else c.label
    val annotated = remember(text, size, unitWeight, firstNumberOnly, c) {
        val unit = SpanStyle(fontSize = size, fontWeight = unitWeight, color = c.label2, letterSpacing = 0.em)
        buildAnnotatedString {
            var numbersSeen = 0
            var i = 0
            while (i < text.length) {
                val number = text.isNumberAt(i)
                var j = i
                while (j < text.length && text.isNumberAt(j) == number) j++
                val run = text.substring(i, j)
                if (number && (!firstNumberOnly || numbersSeen == 0)) {
                    append(run)
                    numbersSeen++
                } else {
                    if (number) numbersSeen++
                    withStyle(unit) { append(run) }
                }
                i = j
            }
        }
    }
    BasicText(annotated, modifier, style = numberStyle.copy(color = ink), softWrap = false, maxLines = 1)
}

/** A hairline across the top of a row inside a card, from [start] to [end] in from the card's edges. */
internal fun Modifier.topHairline(color: Color, start: Dp, end: Dp = 0.dp): Modifier = drawBehind {
    val w = .5.dp.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    val s = (if (rtl) end else start).toPx()
    val e = size.width - (if (rtl) start else end).toPx()
    drawLine(color, Offset(s, w / 2f), Offset(e, w / 2f), w)
}

/** A text size that grows with the font scale only up to [max]: for text in a fixed place (inside the ring). */
@Composable
internal fun TextUnit.cappedAt(max: Dp): TextUnit {
    val density = LocalDensity.current
    return with(density) { if (toPx() > max.toPx()) max.toSp() else this@cappedAt }
}
