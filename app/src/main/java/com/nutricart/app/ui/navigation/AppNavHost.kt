package com.nutricart.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import com.nutricart.app.ui.dashboard.DashboardScreen
import com.nutricart.app.ui.diary.DiaryScreen
import com.nutricart.app.ui.diary.FoodSearchScreen
import com.nutricart.app.ui.diary.FoodSearchViewModel
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSpinner
import com.nutricart.app.ui.ember.FloatingTabBar
import com.nutricart.app.ui.ember.LocalNavAnimatedScope
import com.nutricart.app.ui.ember.LocalSharedTransitionScope
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.ember.TabItem
import com.nutricart.app.ui.fridge.FridgeScreen
import com.nutricart.app.ui.mealplan.MealPlanScreen
import com.nutricart.app.ui.mealplan.RecipeDetailScreen
import com.nutricart.app.ui.onboarding.OnboardingScreen
import com.nutricart.app.ui.quickadd.QuickAddSheet
import com.nutricart.app.ui.settings.SettingsScreen
import java.time.LocalDate

/** Route names in one place, so there are no magic strings scattered around. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val PLAN = "meal_plan"
    const val FRIDGE = "fridge"
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
 * The title of the screen a stacked screen (Recipe, Settings, Food search) goes back to, for its
 * "‹ Meal plan" link: "Today", "Meal plan", "Fridge" or "Food diary", falling back to "Back". Null
 * outside the app shell (screenshot tests pass the label themselves).
 */
val LocalBackLabel = staticCompositionLocalOf<String?> { null }

/**
 * "Back from Add" (§3.4 A5): after Food search logs to a meal and pops, the screen below it (Today or
 * the Diary) finds that meal in [slot], shows its "Added to Lunch" toast once and calls [consume].
 */
@Immutable
class AddedHandOff(val slot: MealSlot?, val consume: () -> Unit)

/** Provided by the app shell to Today and the Diary; null elsewhere (and in screenshot tests). */
val LocalAddedHandOff = compositionLocalOf<AddedHandOff?> { null }

/** The saved-state key Food search hands the logged meal through (UI layer only). */
private const val ADDED_KEY = "ember.added"

/**
 * Top of the UI tree. The screen simply follows the saved flag:
 * while it is unknown we show a loader; while onboarding is not finished we show
 * the questionnaire; once onboarding saves "completed = true" this recomposes
 * and the whole app switches to the main shell (a short cross-fade). One
 * mechanism, no navigation calls needed for the switch.
 */
@Composable
fun AppRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val completed by mainViewModel.onboardingCompleted.collectAsState()
    Crossfade(targetState = completed, animationSpec = tween(300, easing = EmberEasing.Out), label = "root") { state ->
        when (state) {
            null -> RootLoader()
            false -> OnboardingScreen()
            true -> AppShell()
        }
    }
}

/** A few frames while the saved flag loads: the page colour and a 28 dp Ember spinner, nothing more. */
@Composable
private fun RootLoader() {
    Box(Modifier.fillMaxSize().background(Ember.colors.bg), contentAlignment = Alignment.Center) {
        EmberSpinner(size = 28.dp, contentDescription = stringResource(R.string.loading))
    }
}

/**
 * The app shell: the page (a NavHost on a stage that recedes while a sheet presents), the floating
 * tab bar over it, and the quick-add sheet. Insets are not padded here: every screen's
 * LargeTitleScaffold owns them, so nothing is padded twice.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    // The bar keeps its pill on the last tab while a stacked screen covers it, so it slides away
    // and comes back without the pill jumping.
    var lastTab by rememberSaveable { mutableStateOf(Routes.DASHBOARD) }
    LaunchedEffect(currentRoute) { if (currentRoute in TAB_ROUTES) lastTab = currentRoute }
    val presentation = remember { SheetPresentation() }
    val motion = rememberNavMotion()

    // Pops safely: after the first pop there is nothing behind, and popping the
    // start destination would leave a blank screen (double-tap protection).
    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    CompositionLocalProvider(LocalSheetPresentation provides presentation) {
        Box(Modifier.fillMaxSize()) {
            SheetStage {
                // The day ring flies between Food search and Today / the Diary (back from Add).
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.DASHBOARD,
                            modifier = Modifier.fillMaxSize(),
                            // Tabs cross-fade with a 10 dp rise (a sideways slide would wrongly
                            // suggest they are a stack); stacked screens push in from the side
                            // and pop back out (seekable, so predictive back can scrub them).
                            enterTransition = { if (betweenTabs()) motion.tabEnter else motion.pushEnter },
                            exitTransition = { if (betweenTabs()) motion.tabExit else motion.pushExit },
                            popEnterTransition = { if (betweenTabs()) motion.tabEnter else motion.popEnter },
                            popExitTransition = { if (betweenTabs()) motion.tabExit else motion.popExit },
                        ) {
                            screen(Routes.DASHBOARD) { entry ->
                                AddedReceiver(entry) {
                                    DashboardScreen(
                                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                        onOpenRecipe = { recipeId, portionFactor ->
                                            navController.navigate(Routes.recipe(recipeId, portionFactor))
                                        },
                                    )
                                }
                            }
                            screen(Routes.PLAN) {
                                MealPlanScreen(
                                    onOpenRecipe = { recipeId, portionFactor ->
                                        navController.navigate(Routes.recipe(recipeId, portionFactor))
                                    },
                                )
                            }
                            screen(
                                route = Routes.RECIPE,
                                arguments = listOf(
                                    navArgument("recipeId") { type = NavType.LongType },
                                    navArgument("factor") { type = NavType.FloatType },
                                ),
                            ) { entry ->
                                Stacked(navController, entry) { RecipeDetailScreen(onBack = goBack) }
                            }
                            screen(Routes.FRIDGE) {
                                FridgeScreen(
                                    onOpenRecipe = { recipeId, portionFactor ->
                                        navController.navigate(Routes.recipe(recipeId, portionFactor))
                                    },
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                )
                            }
                            screen(Routes.DIARY) { entry ->
                                AddedReceiver(entry) {
                                    DiaryScreen(
                                        onAddFood = { epochDay, slot ->
                                            navController.navigate(Routes.foodSearch(epochDay, slot))
                                        },
                                    )
                                }
                            }
                            screen(Routes.SETTINGS) { entry ->
                                Stacked(navController, entry) { SettingsScreen(onBack = goBack) }
                            }
                            screen(
                                route = Routes.FOOD_SEARCH,
                                arguments = listOf(
                                    navArgument("epochDay") { type = NavType.LongType },
                                    navArgument("slot") { type = NavType.StringType },
                                    navArgument("autoScan") { type = NavType.BoolType },
                                ),
                            ) { entry ->
                                // The same entry-scoped ViewModel the screen uses: onDone runs both
                                // for the back arrow and after a log, and only a log is handed on.
                                val viewModel: FoodSearchViewModel = hiltViewModel()
                                val below = remember(entry.id) { entryBelow(navController, entry) }
                                Stacked(navController, entry) {
                                    FoodSearchScreen(
                                        onDone = {
                                            if (viewModel.uiState.value.logged) {
                                                below?.savedStateHandle?.set(ADDED_KEY, viewModel.mealSlot.name)
                                            }
                                            goBack()
                                        },
                                        viewModel = viewModel,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Over the page, not in it: the bar never fades with a screen. It leaves on stacked
            // screens and while a sheet presents.
            AppBottomBar(
                currentRoute = lastTab,
                onSelectTab = { route -> navController.navigateToTab(route) },
                onQuickAdd = { showQuickAdd = true },
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = currentRoute in TAB_ROUTES && !presentation.presenting,
            )
        }

        // Lives outside the NavHost on purpose: the sheet belongs to the app, not
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
}

/**
 * The floating tab bar (Today · Plan · Fridge · Diary and the separate + for Quick add), filling the
 * width at the bottom of whatever it is aligned in. It takes no layout space from the page; the
 * scroll-edge fade under it belongs to the page (LargeTitleScaffold draws it under its docked button
 * and toasts). Stateless: AppNavHost passes the current route and does the navigating (screenshot
 * tests reuse it). [visible] = false slides it away.
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onSelectTab: (route: String) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val items = listOf(
        TabItem(Routes.DASHBOARD, stringResource(R.string.tab_today), EmberIcons.Day),
        TabItem(Routes.PLAN, stringResource(R.string.tab_plan), EmberIcons.Plan),
        TabItem(Routes.FRIDGE, stringResource(R.string.fridge_title), EmberIcons.Fridge),
        TabItem(Routes.DIARY, stringResource(R.string.tab_diary), EmberIcons.Diary),
    )
    // The + announces once ("Quick add"); there is no visible label to repeat it.
    FloatingTabBar(
        items = items,
        selectedRoute = currentRoute,
        onSelect = onSelectTab,
        onAdd = onQuickAdd,
        addLabel = stringResource(R.string.quick_add_title),
        modifier = modifier.fillMaxWidth(),
        visible = visible,
    )
}

/** A destination that hands its AnimatedVisibilityScope to the shared day ring (Shared.kt). */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

/** A stacked screen: its back link names the screen below it. */
@Composable
private fun Stacked(navController: NavHostController, entry: NavBackStackEntry, content: @Composable () -> Unit) {
    // Fixed when the screen first appears: the back stack moves under it while it leaves.
    val belowRoute = remember(entry.id) { entryBelow(navController, entry)?.destination?.route }
    val label = stringResource(
        when (belowRoute) {
            Routes.DASHBOARD -> R.string.dashboard_title
            Routes.PLAN -> R.string.plan_title
            Routes.FRIDGE -> R.string.fridge_title
            Routes.DIARY -> R.string.diary_title
            else -> R.string.back
        },
    )
    CompositionLocalProvider(LocalBackLabel provides label, content = content)
}

/** Today and the Diary: the meal Food search just logged to, for their "Added to …" toast. */
@Composable
private fun AddedReceiver(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val handle = entry.savedStateHandle
    val slotName by handle.getStateFlow<String?>(ADDED_KEY, null).collectAsState()
    val handOff = remember(slotName) {
        AddedHandOff(slot = slotName?.let { name -> MealSlot.entries.firstOrNull { it.name == name } }) {
            handle[ADDED_KEY] = null
        }
    }
    CompositionLocalProvider(LocalAddedHandOff provides handOff, content = content)
}

/**
 * The destination entry right under [entry] (graphs are skipped). Asked when [entry] first appears,
 * which is when it is on top; otherwise null, and the link says "Back". The whole back stack is
 * library-internal API, so only the public previous entry is read.
 */
private fun entryBelow(navController: NavHostController, entry: NavBackStackEntry): NavBackStackEntry? =
    navController.previousBackStackEntry.takeIf { navController.currentBackStackEntry?.id == entry.id }

/** Standard bottom-bar navigation: one copy of each tab, tab state preserved. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val TAB_ROUTES =
    setOf(Routes.DASHBOARD, Routes.PLAN, Routes.FRIDGE, Routes.DIARY)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

/**
 * Screen transitions (§3.5), in pixels for this density. Tabs: the new page fades in rising 10 dp
 * while the old one fades and shrinks to .985. Push: in from 24 dp to the side. Pop (button, system
 * or predictive back): the page below slides back from the other side, the top one leaves 48 dp
 * sideways shrinking to .94. Exits are about 40% shorter than entrances; "Remove animations" makes
 * them instant (Compose scales every tween by the animator scale).
 */
private class NavMotion(density: Density, direction: Int) {
    private val rise = with(density) { 10.dp.roundToPx() }
    private val shift = with(density) { 24.dp.roundToPx() } * direction
    private val away = with(density) { 48.dp.roundToPx() } * direction

    val tabEnter: EnterTransition =
        fadeIn(tween(360, 60, EmberEasing.Out)) + slideInVertically(tween(360, 60, EmberEasing.Out)) { rise }
    val tabExit: ExitTransition =
        fadeOut(tween(200, easing = EmberEasing.In)) + scaleOut(tween(200, easing = EmberEasing.In), .985f)
    val pushEnter: EnterTransition =
        fadeIn(tween(360, 60, EmberEasing.Out)) + slideInHorizontally(tween(360, 60, EmberEasing.Smooth)) { shift }
    val pushExit: ExitTransition =
        fadeOut(tween(200, easing = EmberEasing.In)) + scaleOut(tween(200), .985f)
    val popEnter: EnterTransition =
        fadeIn(tween(300, easing = EmberEasing.Out)) + slideInHorizontally(tween(300, easing = EmberEasing.Smooth)) { -shift }
    val popExit: ExitTransition =
        fadeOut(tween(250, easing = EmberEasing.In)) + slideOutHorizontally(tween(250)) { away } + scaleOut(tween(250), .94f)
}

@Composable
private fun rememberNavMotion(): NavMotion {
    val density = LocalDensity.current
    // Right-to-left layouts push from the left.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    return remember(density, direction) { NavMotion(density, direction) }
}
