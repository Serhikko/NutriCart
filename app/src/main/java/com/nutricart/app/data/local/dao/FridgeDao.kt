package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.FridgeItemEntity
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.domain.model.Aisle
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeDao {

    // No ORDER BY on purpose: `aisle` is stored as TEXT, so SQL would sort the
    // enum names alphabetically instead of in store-walk order. Every consumer
    // sorts in Kotlin — same rule as the shopping list.
    @Query("SELECT * FROM fridge_item")
    fun observeAll(): Flow<List<FridgeItemEntity>>

    @Query("SELECT * FROM fridge_item")
    suspend fun allItems(): List<FridgeItemEntity>

    @Query("SELECT * FROM fridge_item WHERE ingredientName = :name")
    suspend fun byName(name: String): FridgeItemEntity?

    @Upsert
    suspend fun upsert(item: FridgeItemEntity)

    @Query("DELETE FROM fridge_item WHERE ingredientName = :name")
    suspend fun delete(name: String)

    /** Buying more of something ADDS to it; it never overwrites. */
    @Transaction
    suspend fun addGrams(name: String, aisle: Aisle, grams: Double) {
        val existing = byName(name)
        upsert(FridgeItemEntity(name, aisle, (existing?.grams ?: 0.0) + grams))
    }

    /** An absolute correction from the edit dialog; 0 g means "gone". */
    @Transaction
    suspend fun setGrams(name: String, aisle: Aisle, grams: Double) {
        if (grams <= FridgeMath.EMPTY_EPSILON_G) delete(name) else upsert(FridgeItemEntity(name, aisle, grams))
    }

    @Query("UPDATE fridge_item SET grams = grams - :grams WHERE ingredientName = :name")
    suspend fun subtract(name: String, grams: Double)

    @Query("DELETE FROM fridge_item WHERE ingredientName IN (:names) AND grams <= :epsilon")
    suspend fun dropEmpty(names: List<String>, epsilon: Double)

    /**
     * Cooking. Ingredients the fridge never had simply match no row — cooking
     * with unregistered food is a no-op by construction, which is why nothing
     * has to check the stock first. Anything that falls to dust is removed, so
     * "nothing left" always means "no row" and never "0.000004 g".
     */
    @Transaction
    suspend fun takeOut(uses: List<FridgeMath.Use>) {
        uses.forEach { subtract(it.name, it.grams) }
        dropEmpty(uses.map { it.name }, FridgeMath.EMPTY_EPSILON_G)
    }

    // Declared on THIS dao although it writes the shopping table: Room checks
    // queries against the whole schema, and it is the only way to make the
    // move below one transaction. Two transactions would let a kill in between
    // re-arm the double-add this flag exists to prevent.
    @Query("UPDATE shopping_list_item SET movedToFridge = 1 WHERE id IN (:ids)")
    suspend fun markMoved(ids: List<Long>)

    /** "Bought it": stock goes up and those rows can never be moved twice. */
    @Transaction
    suspend fun putBought(additions: List<FridgeItemEntity>, shoppingIds: List<Long>) {
        additions.forEach { addGrams(it.ingredientName, it.aisle, it.grams) }
        markMoved(shoppingIds)
    }
}
