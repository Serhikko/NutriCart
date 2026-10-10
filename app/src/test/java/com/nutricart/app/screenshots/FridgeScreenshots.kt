package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nutricart.app.R
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeContent
import com.nutricart.app.ui.fridge.FridgeStockContent
import com.nutricart.app.ui.fridge.FridgeUiState
import com.nutricart.app.ui.navigation.Routes
import com.nutricart.app.ui.shopping.ShareListButton
import com.nutricart.app.ui.shopping.ShoppingContent
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
    private fun ToBuy(state: ShoppingUiState) = TabFrame(Routes.FRIDGE) {
        FridgeContent(
            snackbarHostState = remember { SnackbarHostState() },
            shareAction = { ShareListButton(enabled = state.hasList, onClick = {}) },
            stockBody = {},
            shoppingBody = {
                ShoppingContent(
                    state = state,
                    onToggleDay = {},
                    onRegenerate = {},
                    onSetChecked = { _, _ -> },
                    onToggleHave = {},
                    onMoveBought = {},
                    onCopy = {},
                )
            },
            initialShowStock = false,
        )
    }

    @Test
    @KeyScreen
    fun stock() = shoot("fridge-stock") { Stock(FakeData.fridge()) }

    @Test
    @Tall(1700)
    fun stockAiAnswer() = shoot("fridge-stock-ai-answer") { Stock(FakeData.fridge(), FakeData.aiAnswer()) }

    @Test
    fun stockAiAsking() = shoot("fridge-stock-ai-asking") {
        Stock(FakeData.fridge(), AiUiState(hasKey = true, loading = true))
    }

    @Test
    fun stockEmpty() = shoot("fridge-stock-empty") { Stock(FakeData.fridgeEmpty()) }

    @Test
    fun addDialog() = shoot(
        "fridge-add-dialog",
        interact = { onNodeWithText(str(R.string.fridge_add_action)).performClick() },
    ) { Stock(FakeData.fridge()) }

    @Test
    fun editDialog() = shoot(
        "fridge-edit-dialog",
        interact = { onNodeWithText("Броколі").performClick() },
    ) { Stock(FakeData.fridge()) }

    @Test
    @KeyScreen
    fun shopping() = shoot("shopping-list") { ToBuy(FakeData.shopping()) }

    @Test
    @Tall(1500)
    fun shoppingFull() = shoot("shopping-list-full") { ToBuy(FakeData.shopping()) }

    @Test
    fun shoppingNoPlan() = shoot("shopping-no-plan") { ToBuy(FakeData.shoppingNoPlan()) }
}
