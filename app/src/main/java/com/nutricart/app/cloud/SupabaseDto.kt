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
    /** Set once an email is linked to the (originally anonymous) account. */
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    /** The address waiting for its confirmation click, if any. */
    @SerialName("new_email") val newEmail: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean = true,
)

/** PUT /auth/v1/user: links an email to the account; GoTrue mails a confirmation. */
@Serializable
data class SbUpdateUserRequest(
    val email: String,
)

/*
 * Rows the phone PULLS (milestone 3). Only the columns the phone needs to
 * apply a change locally; ignoreUnknownKeys covers the rest.
 */

@Serializable
data class SbFoodRowDto(
    val id: String,
    @SerialName("epoch_day") val epochDay: Long,
    val meal: String,
    val name: String,
    val grams: Double? = null,
    val servings: Double? = null,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fiber_g") val fiberG: Double? = null,
    @SerialName("sugars_g") val sugarsG: Double? = null,
    @SerialName("salt_g") val saltG: Double? = null,
    @SerialName("saturated_fat_g") val saturatedFatG: Double? = null,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SbWaterRowDto(
    val id: String,
    @SerialName("epoch_day") val epochDay: Long,
    val ml: Int,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SbWeightRowDto(
    @SerialName("epoch_day") val epochDay: Long,
    val source: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("updated_at") val updatedAt: String,
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
