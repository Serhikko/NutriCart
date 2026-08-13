package com.nutricart.app.data.remote

import com.nutricart.app.data.remote.dto.ProductResponseDto
import com.nutricart.app.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts — free food database, no API key needed.
 * NOTE: free-text name search only exists on the LEGACY endpoint (search.pl);
 * the v2 API can only filter by tags, so we use v2 solely for barcode lookup.
 * The `fields` parameter keeps responses small (only what we map).
 */
interface OpenFoodFactsApi {

    @GET("cgi/search.pl?search_simple=1&action=process&json=1")
    suspend fun searchByName(
        @Query("search_terms") query: String,
        @Query("page_size") pageSize: Int = 25,
        @Query("fields") fields: String = FIELDS,
    ): SearchResponseDto

    // Used by the barcode scanner (FoodRepository.byBarcode).
    @GET("api/v2/product/{barcode}")
    suspend fun productByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): ProductResponseDto

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        // OFF returns ONLY the requested fields — a field missing here is
        // silently absent from every response (review-caught: the additives
        // feature shipped dead because additives_tags wasn't listed).
        const val FIELDS = "code,product_name,brands,nutriments,serving_quantity,additives_tags"
    }
}
