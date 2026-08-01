package com.nutricart.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nutricart.app.MainViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.dashboard.DashboardScreen
import com.nutricart.app.ui.diary.DiaryScreen
import com.nutricart.app.ui.diary.FoodSearchScreen
import com.nutricart.app.ui.mealplan.MealPlanScreen
import com.nutricart.app.ui.mealplan.RecipeDetailScreen
import com.nutricart.app.ui.onboarding.OnboardingScreen
import com.nutricart.app.ui.settings.SettingsScreen
import com.nutricart.app.ui.shopping.ShoppingScreen

/** Route names in one place, so there are no magic strings scattered around. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val PLAN = "meal_plan"
    const val SHOPPING = "shopping"
    const val DIARY = "diary"
    const val SETTINGS = "settings"

    // Patterns with placeholders + helpers that fill them in.
    const val FOOD_SEARCH = "food_search/{epochDay}/{slot}"
    fun foodSearch(epochDay: Long, slot: MealSlot) = "food_search/$epochDay/${slot.name}"

    const val RECIPE = "recipe/{recipeId}/{factor}"
    fun recipe(recipeId: Long, portionFactor: Double) = "recipe/$recipeId/${portionFactor.toFloat()}"
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Pops safely: after the first pop there is nothing behind, and popping the
    // start destination would leave a blank screen (double-tap protection).
    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    Scaffold(
        bottomBar = {
            // The tab bar shows only on the four top-level screens.
            if (currentRoute in TAB_ROUTES) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = { navController.navigateToTab(Routes.DASHBOARD) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_today)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.PLAN,
                        onClick = { navController.navigateToTab(Routes.PLAN) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_plan)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SHOPPING,
                        onClick = { navController.navigateToTab(Routes.SHOPPING) },
                        icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_shopping)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.DIARY,
                        onClick = { navController.navigateToTab(Routes.DIARY) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_diary)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
            // Stacked screens glide in from the right; switching between the
            // bottom-bar tabs cross-fades instead (a sideways slide would
            // wrongly suggest the tabs are a stack).
            enterTransition = {
                if (bothAreTabs(initialState.destination.route, targetState.destination.route)) {
                    fadeIn(tween(220))
                } else {
                    slideInHorizontally(tween(350)) { it / 4 } + fadeIn(tween(350))
                }
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                if (bothAreTabs(initialState.destination.route, targetState.destination.route)) {
                    fadeOut(tween(220))
                } else {
                    slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300))
                }
            },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenRecipe = { recipeId, portionFactor ->
                        navController.navigate(Routes.recipe(recipeId, portionFactor))
                    },
                )
            }
            composable(Routes.PLAN) {
                MealPlanScreen(
                    onOpenRecipe = { recipeId, portionFactor ->
                        navController.navigate(Routes.recipe(recipeId, portionFactor))
                    },
                )
            }
            composable(
                route = Routes.RECIPE,
                arguments = listOf(
                    navArgument("recipeId") { type = NavType.LongType },
                    navArgument("factor") { type = NavType.FloatType },
                ),
            ) {
                RecipeDetailScreen(onBack = goBack)
            }
            composable(Routes.SHOPPING) {
                ShoppingScreen()
            }
            composable(Routes.DIARY) {
                DiaryScreen(
                    onAddFood = { epochDay, slot ->
                        navController.navigate(Routes.foodSearch(epochDay, slot))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = goBack)
            }
            composable(
                route = Routes.FOOD_SEARCH,
                arguments = listOf(
                    navArgument("epochDay") { type = NavType.LongType },
                    navArgument("slot") { type = NavType.StringType },
                ),
            ) {
                FoodSearchScreen(onDone = goBack)
            }
            // Future screens (shopping list) are added here.
        }
    }
}

/** Standard bottom-bar navigation: one copy of each tab, tab state preserved. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val TAB_ROUTES = setOf(Routes.DASHBOARD, Routes.PLAN, Routes.SHOPPING, Routes.DIARY)

private fun bothAreTabs(from: String?, to: String?): Boolean =
    from in TAB_ROUTES && to in TAB_ROUTES
