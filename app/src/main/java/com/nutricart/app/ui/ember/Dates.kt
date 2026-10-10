package com.nutricart.app.ui.ember

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.nutricart.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Dates as the screens write them: in the device language's own order and punctuation (Android's
// best pattern for a skeleton), first letter up. One place, so Today, the Diary, Quick add, the plan
// and Statistics never drift apart on how a day or a week reads.

/** The locale's own pattern for a date [skeleton] ("EEEEdMMMM" → "EEEE, MMMM d" in English, "EEEE, d MMMM" in Ukrainian). */
fun bestPattern(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** First letter up, the way the locale does it ("субота" → "Субота"). */
fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

/** "Saturday, October 10" / "Субота, 10 жовтня": the day's full name (eyebrows, TalkBack). */
fun longDate(date: LocalDate, locale: Locale): String =
    date.format(bestPattern(locale, "EEEEdMMMM")).capitalized(locale)

/** [longDate] in the screen's language, remembered per day. */
@Composable
fun rememberLongDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) { longDate(date, locale) }
}

/**
 * A span of days as an eyebrow: "October 4 – 10" / "4 – 10 жовтня" inside one month, "September 11 –
 * October 10" across two (the plan's week, a statistics range). The day number alone stands on the
 * side where the locale puts the day, so the month is written once.
 */
@Composable
fun rememberDateRange(from: LocalDate, to: LocalDate): String {
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
