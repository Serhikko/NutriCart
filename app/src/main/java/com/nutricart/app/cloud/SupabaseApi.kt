package com.nutricart.app.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Supabase Auth (GoTrue), the two calls the phone needs. The `apikey` header
 * is added by an interceptor in NetworkModule; the anon key is public.
 *
 * No SDK on purpose: two small Retrofit interfaces keep the dependency list
 * as it is and read like the rest of the network layer. If the project moves
 * to Kotlin Multiplatform, supabase-kt is the natural replacement.
 */
interface SupabaseAuthApi {

    /** With anonymous sign-ins enabled, an empty body creates an anonymous user. */
    @POST("auth/v1/signup")
    suspend fun signUpAnonymous(@Body body: JsonObject): SbSessionDto

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(@Body body: SbRefreshRequest): SbSessionDto

    /** The signed-in user, with the email state after a link request. */
    @GET("auth/v1/user")
    suspend fun user(@Header("Authorization") bearer: String): SbUserDto

    /**
     * Links an email (and a password) to the anonymous account. GoTrue sends a
     * confirmation to that address; once clicked, the account is permanent and
     * the same email and password sign in on the website from any browser.
     * The body is built by the caller so an absent password is absent, not null.
     */
    @PUT("auth/v1/user")
    suspend fun updateUser(@Header("Authorization") bearer: String, @Body body: JsonObject): SbUserDto
}

/**
 * PostgREST. Every call carries the user's JWT as a per-call header, so a
 * refreshed token takes effect on the next request with no client to rebuild.
 * Row-level security on the server decides what each call may touch.
 */
interface SupabaseRestApi {

    /**
     * Insert-or-update by primary key. `return=minimal` keeps the response
     * empty (201), which is all the outbox needs to know.
     */
    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/{table}")
    suspend fun upsert(
        @Header("Authorization") bearer: String,
        @Path("table") table: String,
        @Body rows: JsonArray,
    ): Response<Unit>

    /**
     * A plain insert (no conflict target): for rows that are only ever created, like a pairing
     * code. Naming a conflict target, as [upsert] does, makes the server check the new row against
     * the table's SELECT policies too.
     */
    @Headers("Prefer: return=minimal")
    @POST("rest/v1/{table}")
    suspend fun insert(
        @Header("Authorization") bearer: String,
        @Path("table") table: String,
        @Body rows: JsonArray,
    ): Response<Unit>

    /** Filters are PostgREST expressions, e.g. mapOf("owner_id" to "eq.<uuid>"). */
    @Headers("Prefer: return=minimal")
    @DELETE("rest/v1/{table}")
    suspend fun delete(
        @Header("Authorization") bearer: String,
        @Path("table") table: String,
        @QueryMap filters: Map<String, String>,
    ): Response<Unit>

    @GET("rest/v1/nudges")
    suspend fun unseenNudges(
        @Header("Authorization") bearer: String,
        @Query("owner_id") ownerFilter: String,
        @Query("seen_at") seenFilter: String = "is.null",
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.asc",
    ): List<SbNudgeDto>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nudges")
    suspend fun patchNudges(
        @Header("Authorization") bearer: String,
        @Query("id") idFilter: String,
        @Body body: JsonObject,
    ): Response<Unit>

    // --- Pull (milestone 3): "everything of mine changed since", oldest first ---

    @GET("rest/v1/food_log_entries")
    suspend fun foodRows(@Header("Authorization") bearer: String, @QueryMap filters: Map<String, String>): List<SbFoodRowDto>

    @GET("rest/v1/water_entries")
    suspend fun waterRows(@Header("Authorization") bearer: String, @QueryMap filters: Map<String, String>): List<SbWaterRowDto>

    @GET("rest/v1/weight_entries")
    suspend fun weightRows(@Header("Authorization") bearer: String, @QueryMap filters: Map<String, String>): List<SbWeightRowDto>

    /** The owner's partners with their display names, embedded via the named FK. */
    @GET("rest/v1/partner_links")
    suspend fun partnerLinks(
        @Header("Authorization") bearer: String,
        @Query("owner_id") ownerFilter: String,
        @Query("select") select: String =
            "id,partner_id,created_at,partner:profiles!partner_links_partner_profile_fkey(display_name)",
    ): List<SbPartnerLinkDto>
}
