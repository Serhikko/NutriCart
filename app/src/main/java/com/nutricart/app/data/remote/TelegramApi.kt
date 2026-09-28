package com.nutricart.app.data.remote

import com.nutricart.app.data.remote.dto.TgMeResponse
import com.nutricart.app.data.remote.dto.TgSendMessageRequest
import com.nutricart.app.data.remote.dto.TgSendResponse
import com.nutricart.app.data.remote.dto.TgUpdatesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Telegram Bot API, called with the USER'S OWN bot token.
 *
 * Why Telegram: the partner has an iPhone, and this is the one channel that
 * reaches it with no backend of ours, no Apple account and no second app —
 * she already has Telegram. The token is created by the user in @BotFather
 * and stored like the AI key (SecretsDataStore, out of cloud backup).
 *
 * The token is part of the URL PATH, so it must never be logged: every catch
 * block in PartnerRepository logs exception class names only.
 */
interface TelegramApi {

    /** Validates the token and tells us the bot's @username. */
    @GET("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): TgMeResponse

    /**
     * Short poll (timeout 0): the worker asks, Telegram answers at once with
     * whatever is queued. [offset] = last seen update_id + 1 acknowledges
     * everything before it, so a message is never processed twice.
     */
    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long?,
        @Query("timeout") timeout: Int = 0,
        @Query("allowed_updates") allowedUpdates: String = "[\"message\"]",
    ): TgUpdatesResponse

    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body body: TgSendMessageRequest,
    ): TgSendResponse

    companion object {
        const val BASE_URL = "https://api.telegram.org/"

        /** "123456789:AAH...": digits, a colon, then the secret part. */
        val TOKEN_PATTERN = Regex("^\\d{5,}:[A-Za-z0-9_-]{30,}$")
    }
}
