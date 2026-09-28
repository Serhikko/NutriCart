package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import kotlin.random.Random

class PairingCodeTest {

    @Test
    fun `generated codes are six characters from the safe alphabet`() {
        repeat(200) {
            val code = PairingCode.generate(Random(it))
            assertTrue(code, PairingCode.isWellFormed(code))
            assertFalse(code, code.any { c -> c in "0O1I" })
        }
    }

    @Test
    fun `normalisation forgives case, spaces and dashes`() {
        assertEquals("7KQ4MD", PairingCode.normalize(" 7kq-4md "))
        assertEquals("7KQ4MD", PairingCode.normalize("7KQ 4MD"))
    }

    @Test
    fun `well-formed rejects wrong length and look-alike letters`() {
        assertTrue(PairingCode.isWellFormed("7KQ4MD"))
        assertFalse(PairingCode.isWellFormed("7KQ4M"))
        assertFalse(PairingCode.isWellFormed("7KQ4MO")) // O is not in the alphabet
        assertFalse(PairingCode.isWellFormed("7kq4md")) // callers normalise first
    }

    @Test
    fun `hash is the SQL side's digest of the upper-cased trimmed code`() {
        // The database computes encode(digest(upper(trim(code)), 'sha256'), 'hex').
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("ABCDEF".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, PairingCode.hash("ABCDEF"))
        assertEquals(expected, PairingCode.hash(" abc def "))
        assertEquals(64, expected.length)
    }
}
