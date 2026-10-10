package com.nutricart.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nutricart.app.PermissionsRationaleContent
import com.nutricart.app.R
import com.nutricart.app.ui.ember.BackLink
import com.nutricart.app.ui.ember.BottomClearance
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.DayNav
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberConfirmSheet
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.navigation.AppBottomBar
import com.nutricart.app.ui.navigation.Routes
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Ember chrome (wave 1, B3): the floating tab bar and its stretching pill, the large-title page
 * at rest and scrolled (compact bar), a stacked page with its back link and docked button, pull to
 * refresh, toasts, the floating sheet over the receding page, the confirmation sheet, and the
 * Health Connect rationale page. Compare with app-design/shots/<screen>-<theme>.png and the web's phone shots.
 */
class EmberChromeScreenshots(variant: Variant) : ScreenshotTest(variant) {

    // ------------------------------------------------------------------ helpers

    /** "Saturday 10 October" for today, in the shot's language (an eyebrow like Today's). */
    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** Enough plain cards to scroll a page. */
    private fun LazyListScope.demoCards() {
        val cards = listOf(
            Triple(R.string.today_menu_title, EmberIcons.Plan, Metric.Kcal),
            Triple(R.string.macros_title, EmberIcons.Bolt, Metric.Protein),
            Triple(R.string.steps_label, EmberIcons.Steps, Metric.Steps),
            Triple(R.string.water_label, EmberIcons.Drop, Metric.Water),
            Triple(R.string.weight_card_title, EmberIcons.Scale, Metric.Weight),
            Triple(R.string.ingredients_title, EmberIcons.Utensils, Metric.Carbs),
        )
        cards.forEachIndexed { i, (label, icon, metric) ->
            item(key = "card-$i") {
                val c = Ember.colors
                EmberCard {
                    CardHead(stringResource(label), icon = icon, metric = metric)
                    BasicText(
                        stringResource(R.string.fridge_cooked_hint),
                        style = Ember.type.callout,
                        color = { c.label2 },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun TodayPage(
        scrolled: Boolean = false,
        toast: SnackbarHostState? = null,
        refreshing: Boolean = false,
    ) {
        LargeTitleScaffold(
            title = stringResource(R.string.dashboard_title),
            eyebrow = today(),
            compactTitle = stringResource(R.string.compact_left, stringResource(R.string.dashboard_title), "855"),
            actions = {
                EmberIconButton(EmberIcons.Settings, stringResource(R.string.settings_title), {}, style = IconButtonStyle.Surface)
            },
            below = {
                DayNav(
                    label = stringResource(R.string.tab_today),
                    onPrevious = {}, onNext = {}, nextEnabled = false,
                    previousLabel = stringResource(R.string.previous_day),
                    nextLabel = stringResource(R.string.next_day),
                )
            },
            listState = rememberLazyListState(initialFirstVisibleItemIndex = if (scrolled) 1 else 0),
            toastHostState = toast,
            onRefresh = if (refreshing) ({}) else null,
            refreshing = refreshing,
        ) { demoCards() }
    }

    @Composable
    private fun RecipePage(scrolled: Boolean = false, toast: SnackbarHostState? = null) {
        val name = stringResource(R.string.fridge_cooked_title)
        LargeTitleScaffold(
            title = name,
            eyebrow = stringResource(R.string.meal_lunch) + " · " + stringResource(R.string.tab_today),
            back = BackLink(stringResource(R.string.plan_title)) {},
            compactTitle = name,
            bottom = BottomClearance.DockedCta,
            dockedCta = {
                EmberButton(
                    stringResource(R.string.fridge_cooked_action), {},
                    Modifier.fillMaxWidth().emberShadow(Elevation.Float, EmberShapes.capsule, Ember.colors.isDark),
                    variant = ButtonVariant.Ink, size = ButtonSize.Lg, icon = EmberIcons.Pot,
                )
            },
            listState = rememberLazyListState(initialFirstVisibleItemIndex = if (scrolled) 1 else 0),
            toastHostState = toast,
        ) { demoCards() }
    }

    /** A page on the receding stage with the tab bar over it, as the app shell lays them out. */
    @Composable
    private fun Shell(route: String?, page: @Composable () -> Unit, sheet: @Composable () -> Unit) {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            Box(Modifier.fillMaxSize()) {
                SheetStage { page() }
                if (route != null) {
                    AppBottomBar(
                        route, {}, {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        visible = !presentation.presenting,
                    )
                }
            }
            sheet()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DemoSheet() {
        val c = Ember.colors
        EmberSheet(onDismissRequest = {}, paneTitle = stringResource(R.string.workout_add)) {
            SheetHeader(
                stringResource(R.string.workout_add),
                subtitle = today(),
                onClose = {},
                closeLabel = stringResource(R.string.cancel),
            )
            BasicText(
                stringResource(R.string.fridge_cooked_hint),
                style = Ember.type.callout,
                color = { c.label2 },
            )
            EmberButton(stringResource(R.string.add_action), {}, Modifier.fillMaxWidth(), size = ButtonSize.Lg, icon = EmberIcons.Plus)
        }
    }

    // ------------------------------------------------------------------ tab bar

    @Test
    @KeyScreen
    fun tabBar() = shoot("chrome-tabbar") {
        Column(
            Modifier.fillMaxSize().background(Ember.colors.bg).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(Routes.DASHBOARD, Routes.PLAN, Routes.FRIDGE, Routes.DIARY, null).forEach { route ->
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    AppBottomBar(route, {}, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    /** Today → Diary: the leading edge runs ahead, the pill stretches, squashes to .92 and settles. */
    @Test
    fun tabBarPill() = shootFrames(
        "chrome-tabbar-pill",
        times = listOf(40, 100, 180, 260, 360, 640),
        firstOpen = false,
        interact = { onNodeWithText(str(R.string.tab_diary)).performClick() },
    ) {
        var route by remember { mutableStateOf(Routes.DASHBOARD) }
        Box(Modifier.fillMaxSize().background(Ember.colors.bg)) {
            AppBottomBar(route, { route = it }, {}, Modifier.align(Alignment.BottomCenter))
        }
    }

    /** First open of the session: the bar rises 24 dp into place (G10). */
    @Test
    fun tabBarIntro() = shootFrames("chrome-tabbar-intro", times = listOf(60, 250, 450, 900)) {
        Box(Modifier.fillMaxSize().background(Ember.colors.bg)) {
            AppBottomBar(Routes.DASHBOARD, {}, {}, Modifier.align(Alignment.BottomCenter))
        }
    }

    // ------------------------------------------------------------------ large title

    @Test
    @KeyScreen
    fun scaffold() = shoot("chrome-scaffold") {
        TabFrame(Routes.DASHBOARD) { TodayPage() }
    }

    @Test
    @KeyScreen
    fun scaffoldScrolled() = shoot("chrome-scaffold-scrolled") {
        TabFrame(Routes.DASHBOARD) { TodayPage(scrolled = true) }
    }

    /** The title rises out of its line, the eyebrow, actions and the row below fade up (G1, G2). */
    @Test
    fun scaffoldOpen() = shootFrames("chrome-scaffold-open", times = listOf(60, 250, 500, 900)) {
        TabFrame(Routes.DASHBOARD) { TodayPage() }
    }

    @Test
    @KeyScreen
    fun stacked() = shoot("chrome-stacked") { RecipePage() }

    @Test
    fun stackedScrolled() = shoot("chrome-stacked-scrolled") { RecipePage(scrolled = true) }

    @Test
    fun refreshing() = shoot("chrome-refreshing") {
        TabFrame(Routes.DASHBOARD) { TodayPage(refreshing = true) }
    }

    // ------------------------------------------------------------------ toasts

    @Test
    @KeyScreen
    fun toast() = shoot("chrome-toast") {
        val host = remember { SnackbarHostState() }
        val text = stringResource(R.string.toast_added_to, stringResource(R.string.meal_lunch))
        LaunchedEffect(Unit) { host.showSnackbar(EmberToastVisuals(text, ToastIcon.Check)) }
        TabFrame(Routes.DASHBOARD) { TodayPage(toast = host) }
    }

    /** A long notice with an action, over a stacked page with a docked button. */
    @Test
    @KeyScreen
    fun toastAction() = shoot("chrome-toast-action") {
        val host = remember { SnackbarHostState() }
        val text = stringResource(R.string.barcode_not_found_ukraine)
        val action = stringResource(R.string.add_action)
        LaunchedEffect(Unit) {
            host.showSnackbar(EmberToastVisuals(text, ToastIcon.Info, actionLabel = action, duration = SnackbarDuration.Long))
        }
        RecipePage(toast = host)
    }

    @Test
    fun toastWarning() = shoot("chrome-toast-warning") {
        val host = remember { SnackbarHostState() }
        val text = stringResource(R.string.sync_failed)
        LaunchedEffect(Unit) { host.showSnackbar(EmberToastVisuals(text, ToastIcon.Warning)) }
        TabFrame(Routes.DASHBOARD) { TodayPage(toast = host) }
    }

    /** G11: up from 18 dp and .9 on the bouncy spring; the check draws on from 220 ms. */
    @Test
    fun toastIn() = shootFrames("chrome-toast-in", times = listOf(60, 200, 360, 640), firstOpen = false) {
        val host = remember { SnackbarHostState() }
        val text = stringResource(R.string.toast_added_to, stringResource(R.string.meal_lunch))
        LaunchedEffect(Unit) { host.showSnackbar(EmberToastVisuals(text, ToastIcon.Check)) }
        TabFrame(Routes.DASHBOARD) { TodayPage(toast = host) }
    }

    // ------------------------------------------------------------------ sheets

    @Test
    @KeyScreen
    fun sheet() = shoot("chrome-sheet") {
        Shell(Routes.DASHBOARD, page = { TodayPage() }) { DemoSheet() }
    }

    /** The sheet rises on the sheet spring while the page recedes onto black and the tab bar leaves. */
    @Test
    fun sheetPresent() = shootFrames(
        "chrome-sheet-present",
        times = listOf(32, 100, 200, 320, 480, 800),
        firstOpen = false,
    ) {
        Shell(Routes.DASHBOARD, page = { TodayPage() }) { DemoSheet() }
    }

    @Test
    @KeyScreen
    fun confirm() = shoot("chrome-confirm") {
        Shell(
            route = null,
            page = {
                LargeTitleScaffold(
                    title = stringResource(R.string.settings_title),
                    back = BackLink(stringResource(R.string.dashboard_title)) {},
                    bottom = BottomClearance.Stacked,
                ) { demoCards() }
            },
        ) {
            EmberConfirmSheet(
                title = stringResource(R.string.reset_confirm_title),
                text = stringResource(R.string.reset_confirm_text),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = {},
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = {},
                icon = EmberIcons.Trash,
                destructive = true,
            )
        }
    }

    @Test
    fun confirmInk() = shoot("chrome-confirm-ink") {
        Shell(Routes.PLAN, page = { TodayPage() }) {
            EmberConfirmSheet(
                title = stringResource(R.string.fridge_cooked_title),
                text = stringResource(R.string.fridge_cooked_hint),
                confirmLabel = stringResource(R.string.fridge_cooked_action),
                onConfirm = {},
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = {},
                icon = EmberIcons.Pot,
            )
        }
    }

    // ------------------------------------------------------------------ rationale activity

    @Test
    @KeyScreen
    fun rationale() = shoot("chrome-rationale") { PermissionsRationaleContent() }
}
