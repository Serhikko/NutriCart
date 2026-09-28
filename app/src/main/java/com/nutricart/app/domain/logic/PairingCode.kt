package com.nutricart.app.domain.logic

import java.security.MessageDigest
import kotlin.random.Random

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

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** What a person types, tidied: upper-case, no spaces or dashes. */
    fun normalize(input: String): String =
        input.trim().uppercase().filterNot { it == ' ' || it == '-' }

    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /** Lower-case hex SHA-256 of the normalised code, the only form stored. */
    fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
