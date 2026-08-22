package com.nutricart.app.data.repository

import android.util.Log
import com.nutricart.app.data.local.entity.FridgeItemEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.remote.ClaudeApi
import com.nutricart.app.data.remote.dto.ClaudeMessageDto
import com.nutricart.app.data.remote.dto.ClaudeRequestDto
import com.nutricart.app.data.settings.SecretsDataStore
import com.nutricart.app.domain.logic.ShoppingListBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Everything that can come back from one "what can I cook?" question. */
sealed interface AiResult {
    /** Free text, exactly as written by the model. Nothing parses it. */
    data class Ok(
        val text: String,
        val inputTokens: Int,
        val outputTokens: Int,
        /** The answer hit the token ceiling and stops mid-sentence. */
        val truncated: Boolean,
    ) : AiResult

    data object NoKey : AiResult
    data object BadKey : AiResult
    data object Offline : AiResult
    data object TooSlow : AiResult
    data object Busy : AiResult
    data object Refused : AiResult
    data object Empty : AiResult
    data object Failed : AiResult
}

/**
 * The one and only thing the AI does here: read the fridge list and write a
 * suggestion in prose.
 *
 * It writes NOTHING to the database, and no number it produces is ever used by
 * the app — kcal, portions, stock and the plan all stay with the deterministic
 * code. Delete this file and the fridge still works; that is the test of
 * whether the feature is real or an AI demo.
 */
@Singleton
class FridgeAiRepository @Inject constructor(
    private val api: ClaudeApi,
    private val secrets: SecretsDataStore,
) {

    /** Whether the assistant is set up at all — the screen asks before offering it. */
    val hasKey: Flow<Boolean> = secrets.aiApiKey.map { !it.isNullOrBlank() }

    suspend fun suggest(
        items: List<FridgeItemEntity>,
        profile: UserProfileEntity,
    ): AiResult {
        val key = secrets.aiApiKey.first()?.trim().orEmpty()
        if (key.isEmpty()) return AiResult.NoKey
        if (items.isEmpty()) return AiResult.Empty

        val request = ClaudeRequestDto(
            model = ClaudeApi.MODEL,
            maxTokens = ClaudeApi.MAX_TOKENS,
            system = systemPrompt(profile),
            messages = listOf(ClaudeMessageDto(role = "user", content = fridgeList(items))),
        )

        return try {
            val response = api.messages(key, ClaudeApi.ANTHROPIC_VERSION, request)

            // stop_reason FIRST: a refusal is a normal 200 whose content can be
            // empty, and reading the text first would show a blank card.
            if (response.stopReason == "refusal") return AiResult.Refused

            // Every text block, not content[0]: a leading block can be empty.
            val text = response.content
                .filter { it.type == "text" }
                .joinToString("\n") { it.text }
                .trim()
            if (text.isEmpty()) return AiResult.Empty

            AiResult.Ok(
                text = text,
                inputTokens = response.usage?.inputTokens ?: 0,
                outputTokens = response.usage?.outputTokens ?: 0,
                truncated = response.stopReason == "max_tokens",
            )
        } catch (e: CancellationException) {
            throw e // never swallow: it would break structured concurrency
        } catch (e: HttpException) {
            // An HttpException is a RuntimeException, NOT an IOException, so it
            // has to be caught before the offline branch or every 401 would be
            // reported as "you are offline".
            when (val code = e.code()) {
                401, 403 -> AiResult.BadKey
                429, 529 -> AiResult.Busy
                in 500..599 -> AiResult.Busy
                else -> {
                    Log.w(TAG, "AI call failed with HTTP $code")
                    AiResult.Failed
                }
            }
        } catch (e: SerializationException) {
            // Must come BEFORE IllegalArgumentException, which it extends:
            // otherwise an unreadable 200 body would be reported to the user as
            // "that key was rejected". Class name only — a decoding message can
            // quote the response body back.
            Log.w(TAG, "AI answer could not be read: ${e.javaClass.simpleName}")
            AiResult.Failed
        } catch (e: IllegalArgumentException) {
            // A key pasted with a stray newline makes OkHttp reject the header
            // — and its message contains the key itself, so this must never be
            // logged with the exception attached.
            AiResult.BadKey
        } catch (e: SocketTimeoutException) {
            AiResult.TooSlow // before IOException: it IS one
        } catch (e: InterruptedIOException) {
            AiResult.TooSlow // the call timeout, also an IOException
        } catch (e: IOException) {
            AiResult.Offline
        } catch (e: Exception) {
            // Class name only. Never the exception object, never its message:
            // both can carry the request URL and the key.
            Log.w(TAG, "AI call failed: ${e.javaClass.simpleName}")
            AiResult.Failed
        }
    }

    /**
     * The fridge as plain text. Only the fridge — the recipe catalogue, the
     * diary and the plan are not sent, which keeps every request small and
     * cheap on the user's own key.
     */
    private fun fridgeList(items: List<FridgeItemEntity>): String =
        items.joinToString("\n") { "- ${it.ingredientName}: ${ShoppingListBuilder.displayGrams(it.grams)} g" }

    /**
     * Diet and allergies go in EVERY time, and this is not optional: the
     * ingredient table has no allergen column, so a dish the model assembles
     * can never be machine-checked the way recipes are. Telling the model is
     * the only protection there is — which is why the screen also says, in
     * plain words, that the app did not verify the answer.
     */
    private fun systemPrompt(profile: UserProfileEntity): String {
        val rules = buildList {
            add("You are a practical home-cooking assistant inside a nutrition app.")
            add("The user lists what is in their fridge. Suggest one or two dishes they can cook from it and say briefly how.")
            add("Keep it short: a few sentences per dish, no preamble, no markdown tables.")
            add("Never invent calorie or macro numbers — the app calculates those itself.")
            if (profile.allergies.isNotEmpty()) {
                val allergens = profile.allergies.joinToString(", ") { it.name.lowercase(Locale.ROOT) }
                add("CRITICAL: the user is allergic to $allergens. Never suggest a dish, garnish or added ingredient that contains these or their derivatives.")
            }
            if (profile.isVegetarian) add("The user is vegetarian: no meat or fish at all.")
            if (profile.noPork) add("The user does not eat pork.")
            add("Answer in the same language as the ingredient names below.")
        }
        return rules.joinToString(" ")
    }

    private companion object {
        const val TAG = "FridgeAi"
    }
}
