package com.nutricart.app.domain.logic

import java.security.SecureRandom

/**
 * A password the app makes up for the cloud account, so the user need not
 * invent one: 14 symbols from an alphabet without look-alikes (no 0/O, 1/l/I),
 * about 80 bits, easy to read off a screen or a photo. The website generates
 * the same shape (web/src/lib/password.ts).
 */
object PasswordGenerator {

    const val LENGTH = 14
    const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val MIN_LENGTH = 8

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun isAcceptable(password: String): Boolean = password.length >= MIN_LENGTH && password.isNotBlank()
}
