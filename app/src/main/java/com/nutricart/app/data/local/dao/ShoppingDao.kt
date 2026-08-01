package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    // No ORDER BY on purpose: SQLite's row order is unspecified anyway, so the
    // aisle-walk sorting is done explicitly in Kotlin where it is consumed.
    @Query("SELECT * FROM shopping_list_item")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_item")
    suspend fun allItems(): List<ShoppingListItemEntity>

    @Insert
    suspend fun insertAll(items: List<ShoppingListItemEntity>)

    @Query("DELETE FROM shopping_list_item")
    suspend fun deleteAll()

    /** The old list disappears and the merged one appears in one transaction. */
    @Transaction
    suspend fun replaceAll(items: List<ShoppingListItemEntity>) {
        deleteAll()
        insertAll(items)
    }

    @Query("UPDATE shopping_list_item SET isChecked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE shopping_list_item SET alreadyHave = :alreadyHave WHERE id = :id")
    suspend fun setAlreadyHave(id: Long, alreadyHave: Boolean)
}
