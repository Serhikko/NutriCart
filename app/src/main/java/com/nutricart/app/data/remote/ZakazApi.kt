package com.nutricart.app.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The zakaz.ua stores API: the shared backend behind the online shops of
 * Auchan, Novus, METRO, EKO Market and other Ukrainian chains, used as a
 * second source for Ukrainian-market products. No key, no documentation, no
 * promises: answers are read as untyped JSON and mapped defensively
 * (ZakazDto.kt), and ZakazShops decides when it is asked at all.
 *
 * Point lookups only, one per scan that Open Food Facts could not answer;
 * the app never crawls this API and never passes its data on.
 */
interface ZakazApi {

    /** Every store: [{id, name, retail_chain, city, is_active, address}, ...]. */
    @GET("stores/")
    suspend fun stores(): JsonElement

    /**
     * One store's product card for [gtin14] (the barcode zero-padded to 14
     * digits). 404 = this store doesn't list it.
     */
    @GET("stores/{store}/products/{gtin14}/")
    suspend fun product(
        @Path("store") storeId: String,
        @Path("gtin14") gtin14: String,
    ): JsonElement

    companion object {
        const val BASE_URL = "https://stores-api.zakaz.ua/"
    }
}
