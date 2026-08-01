package com.nutricart.app.grocery

/** One product a supermarket could offer for a shopping-list line. */
data class GroceryProduct(
    val providerSku: String,
    val title: String,
    val priceMinorUnits: Long?,
)

/** One line the app wants to order: an ingredient and how much of it. */
data class GroceryBasketItem(
    val ingredientName: String,
    val grams: Double,
    val pieces: Int?,
)

/**
 * The contract a real supermarket integration must implement (spec feature 7).
 * V1 ships ONLY [com.nutricart.app.grocery.ManualExportProvider]: real
 * integrations require a commercial partnership and an official API — the app
 * deliberately does not scrape or reverse-engineer any store. When a partner
 * appears, its provider implements these three methods and the shopping screen
 * can offer "order online" without touching the rest of the app.
 */
interface GroceryProvider {

    /** Finds the store's products matching a shopping-list line. */
    suspend fun search(query: String): List<GroceryProduct>

    /** Puts the chosen items into the store's online basket. */
    suspend fun addToBasket(items: List<GroceryBasketItem>): Boolean

    /** A URL that opens the store's checkout with the prepared basket, if any. */
    fun checkoutUrl(): String?
}
