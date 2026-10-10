package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.rememberSheetCloser
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One amount: today's weight. Saving writes a MANUAL weight entry for today,
 * which beats a watch reading for the same day — the same rule the settings
 * form follows. The value lives as typed text so rotation cannot eat it.
 *
 * A sheet with a − [ 78.4 kg ] + stepper (0.1 kg a step, 30–300 kg) prefilled
 * with the latest weight, written with the locale's decimal comma or point; you
 * can also type into it, a comma works as the decimal point. Saving an untouched
 * prefill hands back the stored value exactly, so the "write only if it changed"
 * rule still sees no change. The close button is the old Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWeightDialog(
    currentWeightKg: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val initial = rememberSaveable { if (currentWeightKg > 0.0) kgFieldText(currentWeightKg, locale) else "" }
    var text by rememberSaveable { mutableStateOf(initial) }
    val typed = parseKg(text)
    val weight = typed?.takeIf { it in ProfileOptions.WEIGHT_KG_RANGE }
    val closer = rememberSheetCloser()
    val c = Ember.colors

    // A step lands on the 0.1 kg grid (78.45 → 78.5 / 78.4) and stays inside the allowed range.
    fun step(by: Int) {
        val range = ProfileOptions.WEIGHT_KG_RANGE
        val base = (typed ?: currentWeightKg.takeIf { it > 0.0 } ?: FALLBACK_KG).coerceIn(range)
        val tenths = if (by > 0) floor(base * 10 + 1e-6) + 1 else ceil(base * 10 - 1e-6) - 1
        text = kgFieldText((tenths / 10).coerceIn(range), locale, maxFractionDigits = 1)
    }

    val title = stringResource(R.string.weight_card_title)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        SheetHeader(
            title,
            // The entry is always today's.
            subtitle = stringResource(R.string.tab_today),
            onClose = { closer.close(onDismiss) },
            closeLabel = stringResource(R.string.cancel),
        )
        AmountStepper(
            text = text,
            onTextChange = { text = it },
            unit = stringResource(R.string.quickadd_unit_kg),
            onDecrease = { step(-1) },
            onIncrease = { step(+1) },
            fieldLabel = stringResource(R.string.weight_label),
            decreaseLabel = stringResource(R.string.amount_less),
            increaseLabel = stringResource(R.string.amount_more),
            modifier = Modifier.padding(top = 6.dp),
            isError = text.isNotBlank() && weight == null,
            keyboardType = KeyboardType.Decimal,
        )
        BasicText(
            stringResource(R.string.weight_edit_hint),
            style = Ember.type.footnote,
            color = { c.label2 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        EmberButton(
            stringResource(R.string.save),
            onClick = {
                weight?.let { kg -> closer.close { onConfirm(if (text == initial) currentWeightKg else kg) } }
            },
            modifier = Modifier.padding(top = 4.dp),
            size = ButtonSize.Lg,
            enabled = weight != null,
        )
    }
}

/**
 * Typed kilograms: a comma, a dot or the Arabic decimal mark, spaces ignored, and the digits of any
 * script (a Persian keyboard types "۷۸٫۴"); null when it is not a number.
 */
internal fun parseKg(text: String): Double? = buildString {
    for (ch in text) {
        when {
            ch.isWhitespace() -> Unit
            ch == ',' || ch == ARABIC_DECIMAL_MARK -> append('.')
            ch.isDigit() -> append(ch.digitToInt())
            else -> append(ch)
        }
    }
}.toDoubleOrNull()

/**
 * Kilograms as the field shows them (the prefill and every ± step): at least one decimal, at most
 * [maxFractionDigits], no grouping, the locale's decimal comma or point, and always ASCII digits.
 * The locale's own NumberFormat writes "۷۸٫۴" in Persian or Arabic, which the app (English there)
 * never showed in this field before; ASCII digits keep the field what the old dialog prefilled.
 */
internal fun kgFieldText(kg: Double, locale: Locale, maxFractionDigits: Int = 2): String {
    val comma = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply { decimalSeparator = if (comma) ',' else '.' }
    return DecimalFormat("0.0", symbols).apply {
        isGroupingUsed = false
        maximumFractionDigits = maxFractionDigits
    }.format(kg)
}

/** U+066B, the decimal separator Persian and Arabic keyboards type. */
private const val ARABIC_DECIMAL_MARK = '\u066B'

/** Where − and + start from when there is no weight yet and nothing typed. */
private const val FALLBACK_KG = 70.0
