package com.nutricart.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The request body of POST /v1/messages. Only the fields this app needs.
 *
 * Deliberately NOT sent: `thinking` (on by default on current models and
 * rejected as a budget), `temperature`/`top_p`/`top_k` (removed on current
 * models, they return 400) and any assistant prefill (also removed).
 */
@Serializable
data class ClaudeRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessageDto>,
    @SerialName("output_config") val outputConfig: ClaudeOutputConfigDto,
)

@Serializable
data class ClaudeMessageDto(
    val role: String,
    val content: String,
)

/** effort "low" is the documented setting for short, well-bounded tasks. */
@Serializable
data class ClaudeOutputConfigDto(
    val effort: String = "low",
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
