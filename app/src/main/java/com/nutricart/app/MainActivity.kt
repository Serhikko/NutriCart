package com.nutricart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.reminders.MealReminderScheduling
import com.nutricart.app.ui.navigation.AppRoot
import com.nutricart.app.ui.theme.NutriCartTheme
import com.nutricart.app.widget.NutriCartWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The single Activity: all screens are Compose destinations inside AppRoot. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriCartTheme {
                AppRoot()
            }
        }
        // Self-repair: a reminder chain broken by a one-off failure comes
        // back to life on the next app open.
        lifecycleScope.launch {
            MealReminderScheduling.reanchorAll(this@MainActivity, settings)
        }
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app refreshes the home-screen widget, so whatever was
        // just logged is visible there immediately (the system otherwise
        // refreshes it only every 30 minutes).
        lifecycleScope.launch { NutriCartWidget().updateAll(this@MainActivity) }
    }
}
