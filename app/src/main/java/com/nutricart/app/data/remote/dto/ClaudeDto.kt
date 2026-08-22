package com.nutricart.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The request body of POST /v1/messages. Only the fields this app needs, and
 * that is the whole design: every optional parameter is one more thing that
 * can be rejected by the model you happen to be pointing at.
 *
 * Deliberately NOT sent:
 *  - `output_config.effort` — Haiku 4.5 does not accept it and answers 400;
 *  - `thinking` — a budget is rejected on current models, and Haiku needs none
 *    for a short piece of cooking prose;
 *  - `temperature` / `top_p` / `top_k` and any assistant prefill — removed on
 *    current models, they return 400.
 */
@Serializable
data class ClaudeRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessageDto>,
)

@Serializable
data class ClaudeMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ClaudeResponseDto(
    /** "end_turn", "max_tokens", "refusal"… — ALWAYS read before the content. */
    @SerialName("stop_reason") val stopReason: String? = null,
    val content: List<ClaudeContentDto> = emptyList(),
    val usage: ClaudeUsageDto? = null,
)

/**
 * One block of the answer. There can be several, and a leading one may be
 * empty — so the app concatenates every block of type "text" instead of
 * reading content[0].
 */
@Serializable
data class ClaudeContentDto(
    val type: String = "",
    val text: String = "",
)

@Serializable
data class ClaudeUsageDto(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)
