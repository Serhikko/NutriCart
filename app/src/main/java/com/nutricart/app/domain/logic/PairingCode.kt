package com.nutricart.app.domain.logic

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * The 6-character code that links a partner to an account. The phone shows
 * it; the partner types it into the website; the database compares hashes.
 *
 * The alphabet drops 0/O and 1/I so a code read aloud or from a photo is
 * unambiguous: 32 symbols, 6 places, about a billion combinations, valid for
 * 15 minutes and single-use — plenty against guessing, which the server also
 * rate-limits. Only the SHA-256 of the normalised code is ever stored, and
 * [hash] matches the SQL side exactly: `digest(upper(trim(code)), 'sha256')`.
 */
object PairingCode {

    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val VALIDITY_MINUTES = 15L

    // A token that grants read access: from the platform CSPRNG, like PasswordGenerator (tests pass a seeded Random).
    fun generate(random: Random = secureRandom()): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** What [generate] draws from unless told otherwise; a function of its own so a test can check it. */
    internal fun secureRandom(): Random = SecureRandom().asKotlinRandom()

    /** What a person types, tidied: upper-case, no spaces or dashes. */
    fun normalize(input: String): String =
        input.trim().uppercase().filterNot { it == ' ' || it == '-' }

    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /**
     * The minutes a code still works, rounded up (15:00 and 14:01 left both show 15, the last minute
     * shows 1), or null once it has expired: what the countdown ring shows.
     */
    fun minutesLeft(expiresAtEpochMillis: Long, nowEpochMillis: Long): Int? {
        val left = expiresAtEpochMillis - nowEpochMillis
        return if (left > 0) ((left + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt() else null
    }

    /** How long until [minutesLeft] changes: to the next whole minute left, or to the expiry. */
    fun millisToNextTick(expiresAtEpochMillis: Long, nowEpochMillis: Long): Long =
        ((expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(1) - 1) % MINUTE_MILLIS + 1

    private const val MINUTE_MILLIS = 60_000L

    /** Lower-case hex SHA-256 of the normalised code, the only form stored. */
    fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
