package com.nutricart.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.nutricart.app.ui.ember.EmberTheme

/**
 * The app theme, kept under its old name so MainActivity, PermissionsRationaleActivity and the
 * screenshot tests need no change: it is Ember now (ui/ember/Theme.kt), with Material 3 mapped from
 * the Ember tokens underneath.
 */
@Composable
fun NutriCartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    EmberTheme(darkTheme = darkTheme, content = content)
}
