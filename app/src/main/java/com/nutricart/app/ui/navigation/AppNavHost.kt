package com.nutricart.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.nutricart.app.ui.quickadd.QuickAddSheet
import com.nutricart.app.ui.settings.SettingsScreen
import com.nutricart.app.ui.shopping.ShoppingScreen
import java.time.LocalDate

/** Route names in one place, so there are no magic strings scattered around. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val PLAN = "meal_plan"
    const val SHOPPING = "shopping"
    const val DIARY = "diary"
    const val SETTINGS = "settings"

    // Patterns with placeholders + helpers that fill them in.
    // autoScan = the screen opens straight into the barcode scanner; the quick-add
    // sheet uses it so scanning is two taps from anywhere.
    const val FOOD_SEARCH = "food_search/{epochDay}/{slot}/{autoScan}"
    fun foodSearch(epochDay: Long, slot: MealSlot, autoScan: Boolean = false) =
        "food_search/$epochDay/${slot.name}/$autoScan"

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
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }

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
                    // The centre "+" is a bar SLOT, not a floating button:
                    // Material docks FABs on a BottomAppBar, never on a
                    // NavigationBar, so a real dock would mean hand-rolled
                    // offsets fighting the window insets. As a slot it also
                    // stays evenly spaced with the tabs for free.
                    NavigationBarItem(
                        selected = false,
                        onClick = { showQuickAdd = true },
                        icon = {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.quick_add_title),
                                    // 24 dp icon + 4 + 4 = a 32 dp circle, the
                                    // same height as the tabs' selection pill.
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        },
                        // A label even though the icon speaks for itself: an
                        // unlabelled item is laid out by a different branch of
                        // NavigationBarItem and its icon would sit lower than
                        // the four tabs beside it.
                        label = { Text(stringResource(R.string.add_action)) },
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
                    navArgument("autoScan") { type = NavType.BoolType },
                ),
            ) {
                FoodSearchScreen(onDone = goBack)
            }
            // Future screens (shopping list) are added here.
        }
    }

    // Lives outside the Scaffold on purpose: the sheet belongs to the app, not
    // to one tab, and it renders in its own window anyway. Rows that navigate
    // close it FIRST, so a dismissal can never race a navigation.
    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onLogFood = { slot ->
                showQuickAdd = false
                navController.navigate(Routes.foodSearch(LocalDate.now().toEpochDay(), slot))
            },
            onScanFood = { slot ->
                showQuickAdd = false
                navController.navigate(
                    Routes.foodSearch(LocalDate.now().toEpochDay(), slot, autoScan = true)
                )
            },
        )
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

private val TAB_ROUTES =
    setOf(Routes.DASHBOARD, Routes.PLAN, Routes.SHOPPING, Routes.DIARY)

private fun bothAreTabs(from: String?, to: String?): Boolean =
    from in TAB_ROUTES && to in TAB_ROUTES
