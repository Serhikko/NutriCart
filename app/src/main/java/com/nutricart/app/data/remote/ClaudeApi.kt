package com.nutricart.app.data.remote

import com.nutricart.app.data.remote.dto.ClaudeRequestDto
import com.nutricart.app.data.remote.dto.ClaudeResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * The Claude Messages API, called with the USER'S OWN key.
 *
 * The key travels as a per-call header, not as an OkHttp interceptor: a key
 * changed or deleted in Settings then takes effect on the very next request,
 * with no cached client to invalidate.
 */
interface ClaudeApi {

    @POST("v1/messages")
    suspend fun messages(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String,
        @Body body: ClaudeRequestDto,
    ): ClaudeResponseDto

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_VERSION = "2023-06-01"

        /**
         * The cheapest current model, because this runs on the user's own
         * money and the task is a short piece of cooking prose. Model ids are
         * bare strings — a date suffix is not a valid id and returns 404.
         */
        const val MODEL = "claude-haiku-4-5"

        /**
         * A ceiling, not a budget: billing is per token actually generated, so
         * a low cap saves nothing and only truncates long answers. The real
         * cost levers are the low effort setting and a system prompt that asks
         * for a short answer.
         */
        const val MAX_TOKENS = 8000
    }
}
