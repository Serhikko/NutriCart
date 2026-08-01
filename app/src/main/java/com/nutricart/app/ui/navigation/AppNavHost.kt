package com.nutricart.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nutricart.app.MainViewModel
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.dashboard.DashboardScreen
import com.nutricart.app.ui.onboarding.OnboardingScreen
import com.nutricart.app.ui.settings.SettingsScreen

/** Route names in one place, so there are no magic strings scattered around. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

/**
 * Top of the UI tree. The screen simply follows the saved flag:
 * while it is unknown we show a loader; while onboarding is not finished we show
 * the questionnaire; once onboarding saves "completed = true" this recomposes
 * and the whole app switches to the main NavHost. One mechanism, no navigation
 * calls needed for the switch.
 */
@Composable
fun AppRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val completed by mainViewModel.onboardingCompleted.collectAsState()
    when (completed) {
        null -> LoadingBox()
        false -> OnboardingScreen()
        true -> AppNavHost()
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    // Guard against a double-tap on Back: after the first pop
                    // there is nothing behind, and popping the start destination
                    // would leave a blank screen.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
            )
        }
        // Future screens (diary, meal plan, recipes, shopping list) are added here.
    }
}
