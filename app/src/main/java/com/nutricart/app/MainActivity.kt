package com.nutricart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutricart.app.ui.navigation.AppRoot
import com.nutricart.app.ui.theme.NutriCartTheme
import dagger.hilt.android.AndroidEntryPoint

/** The single Activity: all screens are Compose destinations inside AppRoot. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriCartTheme {
                AppRoot()
            }
        }
    }
}
