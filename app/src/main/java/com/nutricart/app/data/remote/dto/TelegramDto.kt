package com.nutricart.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The handful of Telegram Bot API shapes the partner feature needs. Every
 * field the app does not read is left out; unknown keys are ignored by the
 * Json instance, so Telegram adding fields never breaks decoding.
 */

@Serializable
data class TgUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String? = null,
    val username: String? = null,
) {
    /** "Anna", "Anna Smith" or "@anna" — whatever the account offers. */
    val displayName: String
        get() = listOfNotNull(firstName.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { username?.let { "@$it" } ?: id.toString() }
}

@Serializable
data class TgChat(
    val id: Long,
    /** "private", "group", "supergroup" or "channel". */
    val type: String = "",
)

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long = 0,
    val date: Long = 0,
    val chat: TgChat,
    val from: TgUser? = null,
    val text: String? = null,
)

/** One item of getUpdates. Non-message updates leave [message] null. */
@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
)

@Serializable
data class TgMeResponse(
    val ok: Boolean = false,
    val result: TgUser? = null,
)

@Serializable
data class TgUpdatesResponse(
    val ok: Boolean = false,
    val result: List<TgUpdate> = emptyList(),
)

@Serializable
data class TgSendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    /** Plain text on purpose: no parse_mode, so a dish called "50% cocoa" needs no escaping. */
    val text: String,
)

@Serializable
data class TgSendResponse(
    val ok: Boolean = false,
)
