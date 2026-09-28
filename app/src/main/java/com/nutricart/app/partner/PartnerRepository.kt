package com.nutricart.app.partner

import android.util.Log
import com.nutricart.app.data.remote.TelegramApi
import com.nutricart.app.data.remote.dto.TgSendMessageRequest
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.settings.PartnerLink
import com.nutricart.app.data.settings.SecretsDataStore
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.logic.PartnerDigest
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.widget.WidgetDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Every way one Telegram call can end, for the UI and the workers to react to. */
sealed interface PartnerResult {
    data object Ok : PartnerResult
    /** No bot token stored — the feature is simply not set up. */
    data object NoToken : PartnerResult
    /** No partner chat stored yet. */
    data object NotLinked : PartnerResult
    /** Telegram answered 401/404 to the token. */
    data object BadToken : PartnerResult
    /** connect(): the bot has received no message from anyone yet. */
    data object NoMessageYet : PartnerResult
    /** The partner blocked the bot (403). */
    data object Blocked : PartnerResult
    data object Offline : PartnerResult
    /** Rate limit, conflict or server error — worth a retry later. */
    data object Busy : PartnerResult
    data object Failed : PartnerResult
}

/** One message the partner sent to the bot. */
data class IncomingMessage(
    val updateId: Long,
    val senderName: String,
    val text: String,
)

/** What one poll of the bot's inbox produced; [error] is non-null when the poll itself failed. */
data class PartnerInbox(
    val messages: List<IncomingMessage>,
    val error: PartnerResult?,
)

/**
 * The partner feature's one door to Telegram: verifying the token, linking
 * the partner's chat, sending meal updates and reading their replies.
 *
 * Only meal names and calories ever leave the phone through here. Weight,
 * steps, heart rate and sleep are read from Health Connect for the user's
 * own screen and are deliberately never part of any message.
 */
@Singleton
class PartnerRepository @Inject constructor(
    private val api: TelegramApi,
    private val secrets: SecretsDataStore,
    private val settings: SettingsDataStore,
    private val diaryRepository: DiaryRepository,
    private val widgetDataSource: WidgetDataSource,
) {

    val hasToken: Flow<Boolean> = secrets.telegramBotToken.map { !it.isNullOrBlank() }
    val botUsername: Flow<String?> = settings.partnerBotUsername
    val link: Flow<PartnerLink?> = settings.partnerLink

    /**
     * Stores the token only after Telegram accepted it (getMe), and remembers
     * the bot's @username for the instructions shown to the user. A token
     * change invalidates the link and the update cursor: they belong to the
     * old bot.
     */
    suspend fun saveToken(rawToken: String): PartnerResult {
        val token = rawToken.trim()
        if (!TelegramApi.TOKEN_PATTERN.matches(token)) return PartnerResult.BadToken
        return when (val outcome = telegram { api.getMe(token) }) {
            is Outcome.Error -> outcome.result
            is Outcome.Success -> {
                val me = outcome.value.result ?: return PartnerResult.Failed
                val previous = secrets.telegramBotToken.first()
                secrets.setTelegramBotToken(token)
                settings.setPartnerBotUsername(me.username)
                if (previous != null && previous != token) {
                    settings.setPartnerLink(null)
                    settings.setPartnerUpdateOffset(null)
                }
                PartnerResult.Ok
            }
        }
    }

    /** Forgets the bot AND the partner: without a token nothing can be sent anyway. */
    suspend fun deleteToken() {
        secrets.clearTelegramBotToken()
        settings.setPartnerBotUsername(null)
        settings.setPartnerLink(null)
        settings.setPartnerUpdateOffset(null)
    }

    /**
     * Links the partner: whoever wrote to the bot most recently becomes the
     * partner, their chat the destination. This is why the setup says "ask
     * them to send /start first". Everything in the queue is acknowledged so
     * the inbox worker does not replay the /start as a nudge, and a greeting
     * confirms the link on their side.
     */
    suspend fun connectPartner(greeting: String): PartnerResult {
        val token = token() ?: return PartnerResult.NoToken
        val offset = settings.partnerUpdateOffset.first()
        val updates = when (val outcome = telegram { api.getUpdates(token, offset) }) {
            is Outcome.Error -> return outcome.result
            is Outcome.Success -> outcome.value.result
        }
        val latest = updates.lastOrNull { it.message?.from?.isBot == false }
            ?: return PartnerResult.NoMessageYet
        val message = latest.message ?: return PartnerResult.NoMessageYet
        val from = message.from ?: return PartnerResult.NoMessageYet
        settings.setPartnerLink(PartnerLink(message.chat.id, from.id, from.displayName))
        updates.maxOfOrNull { it.updateId }?.let { settings.setPartnerUpdateOffset(it + 1) }
        return sendText(greeting)
    }

    suspend fun unlinkPartner() {
        settings.setPartnerLink(null)
    }

    /** Plain text to the partner's chat. */
    suspend fun sendText(text: String): PartnerResult {
        val token = token() ?: return PartnerResult.NoToken
        val link = settings.partnerLink.first() ?: return PartnerResult.NotLinked
        return when (val outcome = telegram { api.sendMessage(token, TgSendMessageRequest(link.chatId, text)) }) {
            is Outcome.Error -> outcome.result
            is Outcome.Success -> if (outcome.value.ok) PartnerResult.Ok else PartnerResult.Failed
        }
    }

    /**
     * "Here is what was just logged for lunch, and where the day stands."
     * Reads the diary at SEND time, not at log time: the worker runs a little
     * after the last write, so a basket of five items becomes one message,
     * and an entry deleted in between never gets announced.
     */
    suspend fun sendMealUpdate(
        slot: MealSlot,
        epochDay: Long,
        labels: PartnerDigest.Labels,
    ): PartnerResult {
        val items = items(epochDay).filter { it.slotOrdinal == slot.ordinal }
        if (items.isEmpty()) return PartnerResult.Ok // nothing left to tell
        val (eaten, target) = dayNumbers(epochDay)
        return sendText(PartnerDigest.mealMessage(slot.ordinal, items, eaten, target, labels))
    }

    /** The whole day so far — the answer to /today. */
    suspend fun sendDayDigest(title: String, labels: PartnerDigest.Labels): PartnerResult {
        val today = LocalDate.now().toEpochDay()
        val (eaten, target) = dayNumbers(today)
        return sendText(PartnerDigest.dayMessage(title, items(today), eaten, target, labels))
    }

    /**
     * Reads what the partner wrote since the last poll and acknowledges it.
     * Only messages from the linked PERSON in the linked CHAT count — in a
     * group chat the user's own lines must not come back as notifications.
     */
    suspend fun fetchPartnerMessages(): PartnerInbox {
        val token = token() ?: return PartnerInbox(emptyList(), PartnerResult.NoToken)
        val link = settings.partnerLink.first() ?: return PartnerInbox(emptyList(), PartnerResult.NotLinked)
        val offset = settings.partnerUpdateOffset.first()
        val updates = when (val outcome = telegram { api.getUpdates(token, offset) }) {
            is Outcome.Error -> return PartnerInbox(emptyList(), outcome.result)
            is Outcome.Success -> outcome.value.result
        }
        if (updates.isEmpty()) return PartnerInbox(emptyList(), null)
        val messages = updates.mapNotNull { update ->
            val message = update.message ?: return@mapNotNull null
            val from = message.from ?: return@mapNotNull null
            val text = message.text?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (message.chat.id != link.chatId || from.id != link.userId) return@mapNotNull null
            IncomingMessage(update.updateId, from.displayName, text)
        }
        // Acknowledge everything we saw, including what we ignored.
        settings.setPartnerUpdateOffset(updates.maxOf { it.updateId } + 1)
        return PartnerInbox(messages, null)
    }

    private suspend fun token(): String? = secrets.telegramBotToken.first()?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun items(epochDay: Long): List<PartnerDigest.Item> =
        diaryRepository.observeDay(epochDay).first().map {
            PartnerDigest.Item(it.meal.ordinal, it.name, it.kcal)
        }

    /** Eaten kcal and (for today) the target, from the same math the widget uses. */
    private suspend fun dayNumbers(epochDay: Long): Pair<Double, Double?> {
        val eaten = diaryRepository.observeDayTotals(epochDay).first().kcal
        val target = if (epochDay == LocalDate.now().toEpochDay()) {
            widgetDataSource.today()?.targetKcal?.toDouble()
        } else {
            null
        }
        return eaten to target
    }

    private sealed interface Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>
        data class Error(val result: PartnerResult) : Outcome<Nothing>
    }

    /**
     * Runs one API call and maps every failure. Class names only in the log:
     * an OkHttp message quotes the URL, and the URL contains the token.
     */
    private suspend fun <T> telegram(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Outcome.Error(
            when (val code = e.code()) {
                // 404 is what a malformed token gets ("Not Found"), not a missing endpoint.
                401, 404 -> PartnerResult.BadToken
                403 -> PartnerResult.Blocked
                409, 429 -> PartnerResult.Busy
                in 500..599 -> PartnerResult.Busy
                else -> {
                    Log.w(TAG, "Telegram call failed with HTTP $code")
                    PartnerResult.Failed
                }
            }
        )
    } catch (e: SerializationException) {
        Log.w(TAG, "Telegram answer could not be read: ${e.javaClass.simpleName}")
        Outcome.Error(PartnerResult.Failed)
    } catch (e: IllegalArgumentException) {
        // A token with characters Retrofit refuses in a path — treat as bad.
        Outcome.Error(PartnerResult.BadToken)
    } catch (e: InterruptedIOException) {
        Outcome.Error(PartnerResult.Offline) // timeouts, before the general IOException
    } catch (e: IOException) {
        Outcome.Error(PartnerResult.Offline)
    } catch (e: Exception) {
        Log.w(TAG, "Telegram call failed: ${e.javaClass.simpleName}")
        Outcome.Error(PartnerResult.Failed)
    }

    private companion object {
        const val TAG = "Partner"
    }
}
