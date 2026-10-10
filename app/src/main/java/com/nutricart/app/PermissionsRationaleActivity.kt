package com.nutricart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutricart.app.ui.ember.BottomClearance
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.theme.NutriCartTheme

/**
 * Health Connect opens this screen when the user asks WHY the app wants
 * health permissions. It explains what we read, that it stays on the phone,
 * and what cloud sync uploads when the user turns it on.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriCartTheme {
                PermissionsRationaleContent()
            }
        }
    }
}

/** The rationale page: a large title and the text in a card. No tab bar, no back link (system back closes it). */
@Composable
internal fun PermissionsRationaleContent() {
    val c = Ember.colors
    LargeTitleScaffold(
        title = stringResource(R.string.privacy_rationale_title),
        bottom = BottomClearance.Stacked,
    ) {
        item {
            EmberCard {
                BasicText(stringResource(R.string.privacy_rationale_text), style = Ember.type.body, color = { c.label })
            }
        }
    }
}
