package com.nutricart.app.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The few Supabase shapes the phone reads. Rows the phone WRITES are built as
 * JsonObjects in CloudRows, so a schema change is one place on each side.
 */

@Serializable
data class SbSessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    /** Seconds until the access token expires (Supabase default 3600). */
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: SbUserDto,
)

@Serializable
data class SbUserDto(
    val id: String,
)

@Serializable
data class SbRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class SbNudgeDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("from_name") val fromName: String = "",
    val text: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("seen_at") val seenAt: String? = null,
)

@Serializable
data class SbPartnerLinkDto(
    val id: String,
    @SerialName("partner_id") val partnerId: String,
    @SerialName("created_at") val createdAt: String,
    /** Embedded through the named foreign key; null if the profile row is missing. */
    val partner: SbProfileNameDto? = null,
)

@Serializable
data class SbProfileNameDto(
    @SerialName("display_name") val displayName: String? = null,
)
