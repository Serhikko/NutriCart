package com.nutricart.app.grocery

import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import com.nutricart.app.domain.logic.ShoppingListBuilder
import com.nutricart.app.domain.model.Aisle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The v1 "integration": turns the shopping list into plain text the user can
 * share or paste anywhere (messenger, notes, a supermarket app's search box).
 * search/addToBasket are honest no-ops — there is no store behind this provider.
 */
@Singleton
class ManualExportProvider @Inject constructor() : GroceryProvider {

    override suspend fun search(query: String): List<GroceryProduct> = emptyList()

    override suspend fun addToBasket(items: List<GroceryBasketItem>): Boolean = false

    override fun checkoutUrl(): String? = null

    /**
     * Formats the list grouped by aisle in store-walk (enum) order.
     * "Already have" items are skipped (nothing to buy); checked items keep a
     * filled box so a half-done list stays meaningful when shared.
     * Aisle names AND amount formatting come from the UI layer as functions,
     * so the export follows the app language exactly like the screen does.
     */
    fun buildShareText(
        items: List<ShoppingListItemEntity>,
        aisleLabel: (Aisle) -> String,
        amountLabel: (displayGrams: Int, pieces: Int?) -> String,
    ): String = buildString {
        val toBuy = items
            .filterNot { it.alreadyHave }
            // The DB returns rows in unspecified order — sort where consumed.
            .sortedWith(compareBy({ it.aisle.ordinal }, { it.ingredientName }))
        toBuy.groupBy { it.aisle }.forEach { (aisle, aisleItems) ->
            appendLine(aisleLabel(aisle))
            aisleItems.forEach { item ->
                val box = if (item.isChecked) "[x]" else "[ ]"
                val amount = amountLabel(
                    ShoppingListBuilder.displayGrams(item.totalGrams),
                    item.pieces,
                )
                appendLine("$box ${item.ingredientName} — $amount")
            }
            appendLine()
        }
    }.trimEnd()
}
