package com.nutricart.app.data.remote

import com.nutricart.app.data.remote.dto.OFF_PRODUCT_FIELDS
import com.nutricart.app.data.remote.dto.OFF_SEARCH_FIELDS
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
        @Query("fields") fields: String = SEARCH_FIELDS,
    ): SearchResponseDto

    // Used by the barcode scanner (FoodRepository.byBarcode).
    @GET("api/v2/product/{barcode}")
    suspend fun productByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): ProductResponseDto

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        // OFF returns ONLY the requested fields. The list lives next to
        // ProductDto (a test checks every DTO field is in it) and includes the
        // _uk/_ru/_be and generic names that Ukrainian and Belarusian
        // products are often known by (see ProductNames).
        const val FIELDS = OFF_PRODUCT_FIELDS

        // Search skips OFF's estimates, which only the scanner's form uses.
        const val SEARCH_FIELDS = OFF_SEARCH_FIELDS
    }
}
