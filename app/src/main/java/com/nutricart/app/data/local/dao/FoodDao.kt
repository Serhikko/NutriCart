package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.FoodProductEntity

@Dao
interface FoodDao {

    // Offline fallback search over the cache. LIKE '%query%' is simple and
    // fine for a personal cache of at most a few thousand rows.
    @Query(
        """
        SELECT * FROM food_product
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT 50
        """
    )
    suspend fun searchByName(query: String): List<FoodProductEntity>

    @Query("SELECT * FROM food_product WHERE id = :id")
    suspend fun byId(id: String): FoodProductEntity?

    // @Upsert only — see the warning in FoodProductEntity about REPLACE.
    @Upsert
    suspend fun upsertAll(products: List<FoodProductEntity>)
}
