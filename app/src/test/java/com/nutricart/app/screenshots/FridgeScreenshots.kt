package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.nutricart.app.R
import com.nutricart.app.screenshots.PlanFakes.tick
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeContent
import com.nutricart.app.ui.fridge.FridgeStockContent
import com.nutricart.app.ui.fridge.FridgeUiState
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.shopping.ShareListButton
import com.nutricart.app.ui.shopping.ShoppingContent
import com.nutricart.app.ui.shopping.ShoppingItemUi
import com.nutricart.app.ui.shopping.ShoppingUiState
import org.junit.Test

/** The Groceries tab: "in stock" (fridge) and "to buy" (shopping list). */
class FridgeScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Stock(state: FridgeUiState, ai: AiUiState = FakeData.aiNoKey()) = TabFrame(Routes.FRIDGE) {
        FridgeContent(
            snackbarHostState = remember { SnackbarHostState() },
            shareAction = { ShareListButton(enabled = true, onClick = {}) },
            stockBody = {
                FridgeStockContent(
                    state = state,
                    aiState = ai,
                    pickable = FakeData.pickable,
                    onOpenRecipe = { _, _ -> },
                    onOpenSettings = {},
                    onAskAi = {},
                    onDismissAi = {},
                    onAdd = { _, _ -> },
                    onSetGrams = { _, _ -> },
                    onRemove = {},
                )
            },
            shoppingBody = {},
            initialShowStock = true,
        )
    }

    @Composable
    private fun ToBuy(state: ShoppingUiState, onTick: (ShoppingItemUi, Boolean) -> Unit = { _, _ -> }) =
        ToBuy({ state }, onTick)

    /** [state] is read inside the half, so a change recomposes the half alone, as the ViewModel's flow does. */
    @Composable
    private fun ToBuy(state: () -> ShoppingUiState, onTick: (ShoppingItemUi, Boolean) -> Unit) =
        TabFrame(Routes.FRIDGE) {
            FridgeContent(
                snackbarHostState = remember { SnackbarHostState() },
                shareAction = { ShareListButton(enabled = state().hasList, onClick = {}) },
                stockBody = {},
                shoppingBody = {
                    ShoppingContent(
                        state = state(),
                        onToggleDay = {},
                        onRegenerate = {},
                        onSetChecked = onTick,
                        onToggleHave = {},
                        onMoveBought = {},
                        onCopy = {},
                    )
                },
                initialShowStock = false,
            )
        }

    /**
     * The list as the ViewModel keeps it: a tick on a row recounts the basket. Only the half reads the
     * state, so the ticked row reaches the page's list the way it does in the app, through the rows
     * the half hands over, without the page around it recomposing.
     */
    @Composable
    private fun LiveToBuy(holder: MutableState<ShoppingUiState>) =
        ToBuy({ holder.value }, onTick = { item, checked -> holder.value = holder.value.tick(item, checked) })

    /**
     * Large text pushes a control below the fold, where the page's lazy list has not composed it yet:
     * the list scrolls until it exists and is on screen. Scrolling only brings it into the list's
     * viewport, which runs on under the floating tab bar, so a touch there would land on the bar:
     * after the scroll the control's own click action runs instead.
     */
    private fun ComposeContentTestRule.scrollToAndTap(text: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    @KeyScreen
    fun stock() = shoot("fridge-stock") { Stock(FakeData.fridge()) }

    @Test
    @Tall(1900)
    fun stockFull() = shoot("fridge-stock-full") { Stock(FakeData.fridge(), PlanFakes.aiReady()) }

    @Test
    @Tall(1700)
    fun stockAiAnswer() = shoot("fridge-stock-ai-answer") { Stock(FakeData.fridge(), FakeData.aiAnswer()) }

    @Test
    fun stockAiTruncated() = shoot("fridge-stock-ai-truncated") { Stock(FakeData.fridge(), PlanFakes.aiTruncated()) }

    @Test
    fun stockAiAsking() = shoot("fridge-stock-ai-asking") {
        Stock(FakeData.fridge(), AiUiState(hasKey = true, loading = true))
    }

    @Test
    fun stockAiReady() = shoot("fridge-stock-ai-ready") { Stock(FakeData.fridge(), PlanFakes.aiReady()) }

    @Test
    fun stockAiError() = shoot("fridge-stock-ai-error") { Stock(FakeData.fridge(), PlanFakes.aiError()) }

    @Test
    @KeyScreen
    fun stockEmpty() = shoot("fridge-stock-empty") { Stock(FakeData.fridgeEmpty()) }

    @Test
    fun stockEmptyWithKey() = shoot("fridge-stock-empty-key") { Stock(FakeData.fridgeEmpty(), PlanFakes.aiReady()) }

    @Test
    fun stockLoading() = shoot("fridge-stock-loading") { Stock(FridgeUiState()) }

    @Test
    @KeyScreen
    fun addDialog() = shoot(
        "fridge-add-dialog",
        interact = { scrollToAndTap(str(R.string.fridge_add_action)) },
    ) { Stock(FakeData.fridge()) }

    @Test
    fun addDialogPicked() = shoot(
        "fridge-add-dialog-picked",
        interact = {
            scrollToAndTap(str(R.string.fridge_add_action))
            waitForIdle()
            onNodeWithText("Індиче філе").performClick()
        },
    ) { Stock(FakeData.fridge()) }

    @Test
    @KeyScreen
    fun editDialog() = shoot(
        "fridge-edit-dialog",
        interact = { scrollToAndTap("Броколі") },
    ) { Stock(FakeData.fridge()) }

    /** "Add what you have" wrote a new item: it unfolds into its aisle with a short Ember glow. */
    @Test
    @Tall(1900)
    fun stockAddedFrames() {
        val holder = mutableStateOf(FakeData.fridge())
        shootFrames(
            "fridge-stock-added",
            times = listOf(60, 250, 900, 1800),
            firstOpen = false,
            interact = {
                mainClock.advanceTimeBy(1500)
                runOnUiThread { holder.value = PlanFakes.fridgeWithAdded() }
            },
        ) { Stock(holder.value, PlanFakes.aiReady()) }
    }

    @Test
    @KeyScreen
    fun shopping() = shoot("shopping-list") { ToBuy(FakeData.shopping()) }

    @Test
    @Tall(1500)
    fun shoppingFull() = shoot("shopping-list-full") { ToBuy(FakeData.shopping()) }

    @Test
    @KeyScreen
    fun shoppingNoPlan() = shoot("shopping-no-plan") { ToBuy(FakeData.shoppingNoPlan()) }

    @Test
    fun shoppingNothingTicked() = shoot("shopping-list-unticked") { ToBuy(PlanFakes.shoppingNothingTicked()) }

    @Test
    fun shoppingLoading() = shoot("shopping-loading") { ToBuy(ShoppingUiState()) }

    @Test
    fun shoppingGenerating() = shoot("shopping-generating") { ToBuy(FakeData.shopping().copy(generating = true)) }

    /**
     * Ticking "Морква": the disc fills with Ember from the centre while its check draws, a line
     * strikes through the name, the progress bar advances and "Bought 2" hands its digit to 3.
     */
    @Test
    fun tickFrames() {
        val holder = mutableStateOf(FakeData.shopping())
        shootFrames(
            "shopping-tick",
            times = listOf(60, 160, 320, 600),
            firstOpen = false,
            // The page settles first ("Bought 2" has risen in), then "Морква" is ticked.
            interact = {
                mainClock.advanceTimeBy(1500)
                onNodeWithText("Морква").performClick()
            },
        ) { LiveToBuy(holder) }
    }

    /** The first tick: "Bought 1 — put in the fridge" rises in above the tab bar on the sheet spring. */
    @Test
    fun firstTickFrames() {
        val holder = mutableStateOf(PlanFakes.shoppingNothingTicked())
        shootFrames(
            "shopping-first-tick",
            times = listOf(60, 200, 400, 800),
            firstOpen = false,
            interact = {
                mainClock.advanceTimeBy(1500)
                onNodeWithText("Броколі").performClick()
            },
        ) { LiveToBuy(holder) }
    }
}
