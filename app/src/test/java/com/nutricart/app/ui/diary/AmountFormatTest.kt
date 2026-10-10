package com.nutricart.app.ui.diary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The amount sheet's − / + write the stepped amount back into the field, which is read again with
 * `replace(',', '.').toDoubleOrNull()`. Whatever the phone's language, one tap must leave a number
 * that reads back as itself.
 */
class AmountFormatTest {

    private fun readBack(text: String) = text.replace(',', '.').toDoubleOrNull()

    @Test
    fun `English and Ukrainian write the amount as people type it`() {
        assertEquals("110", formatAmount(110.0, Locale.US))
        assertEquals("1.5", formatAmount(1.5, Locale.US))
        assertEquals("1,5", formatAmount(1.5, Locale.forLanguageTag("uk-UA")))
        assertEquals("5000", formatAmount(5000.0, Locale.forLanguageTag("uk-UA"))) // no grouping space
    }

    @Test
    fun `languages with their own digits still get digits the field can read`() {
        for (tag in listOf("fa-IR", "ar-EG", "ar-SA", "bn-BD", "mr-IN", "ne-NP", "my-MM", "hi-IN-u-nu-deva")) {
            val locale = Locale.forLanguageTag(tag)
            for (value in listOf(110.0, 1.5, 0.5, 5000.0)) {
                val text = formatAmount(value, locale)
                assertEquals("$tag: \"$text\"", value, readBack(text)!!, 0.0)
            }
        }
    }
}
