package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.FoodProductEntity

/** How many times one product was logged — feeds frequency-first ranking. */
data class ProductUseCount(
    val productId: String,
    val uses: Int,
)

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

    /**
     * User-created products and products saved from a Ukrainian shop's
     * catalogue, matching the query. Separate from searchByName because
     * these must join ONLINE results too — the Open Food Facts API cannot
     * know about them. ('LOCAL' / 'ZAKAZ' = ProductSource values by name.)
     */
    @Query(
        """
        SELECT * FROM food_product
        WHERE source IN ('LOCAL', 'ZAKAZ')
          AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%')
        ORDER BY name
        LIMIT 20
        """
    )
    suspend fun searchLocalByName(query: String): List<FoodProductEntity>

    @Query("SELECT * FROM food_product WHERE id = :id")
    suspend fun byId(id: String): FoodProductEntity?

    // @Upsert only — see the warning in FoodProductEntity about REPLACE.
    @Upsert
    suspend fun upsertAll(products: List<FoodProductEntity>)

    @Upsert
    suspend fun upsert(product: FoodProductEntity)

    /**
     * Deletes ONLY a user-created product — OFF products stay cached. Diary
     * history survives: food_log_entry keeps its nutrition snapshot and the
     * foreign key just goes null (ON DELETE SET NULL).
     */
    @Query("DELETE FROM food_product WHERE id = :id AND source = 'LOCAL'")
    suspend fun deleteLocal(id: String)

    @Query("UPDATE food_product SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Used to carry the star over when fresh API data overwrites the cache. */
    @Query("SELECT id FROM food_product WHERE isFavorite = 1")
    suspend fun favoriteIds(): List<String>

    // An empty query LIKE '%%' matches everything, so one query serves both
    // "all favorites" and "favorites filtered as the user types".
    @Query(
        """
        SELECT * FROM food_product
        WHERE isFavorite = 1
          AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%')
        ORDER BY name
        """
    )
    suspend fun searchFavorites(query: String): List<FoodProductEntity>

    /**
     * How often each product was logged. Lives here (not in FoodLogDao)
     * because its only consumer is food search ranking.
     */
    @Query(
        """
        SELECT productId, COUNT(*) AS uses FROM food_log_entry
        WHERE productId IS NOT NULL GROUP BY productId
        """
    )
    suspend fun productUseCounts(): List<ProductUseCount>

    /** The user's most-logged products — the "frequent" list under an empty search box. */
    @Query(
        """
        SELECT p.* FROM food_product p
        JOIN (
            SELECT productId, COUNT(*) AS uses FROM food_log_entry
            WHERE productId IS NOT NULL GROUP BY productId
        ) u ON p.id = u.productId
        ORDER BY u.uses DESC, p.name
        LIMIT :limit
        """
    )
    suspend fun frequentProducts(limit: Int): List<FoodProductEntity>
}
