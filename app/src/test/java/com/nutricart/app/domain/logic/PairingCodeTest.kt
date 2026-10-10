package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asJavaRandom

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
    fun `the default generator makes well-formed codes too`() {
        repeat(50) {
            val code = PairingCode.generate()
            assertTrue(code, PairingCode.isWellFormed(code))
        }
    }

    @Test
    fun `the default generator draws from the platform CSPRNG`() {
        // A code grants read access: Random.Default (not a SecureRandom underneath) would fail this.
        assertTrue(PairingCode.secureRandom().asJavaRandom() is SecureRandom)
    }

    @Test
    fun `the countdown rounds the time left up to whole minutes`() {
        val expires = 1_000_000_000L
        assertEquals(15, PairingCode.minutesLeft(expires, expires - 15 * 60_000L))
        assertEquals(15, PairingCode.minutesLeft(expires, expires - 14 * 60_000L - 1))
        assertEquals(14, PairingCode.minutesLeft(expires, expires - 14 * 60_000L))
        assertEquals(1, PairingCode.minutesLeft(expires, expires - 1))
        assertNull(PairingCode.minutesLeft(expires, expires))
        assertNull(PairingCode.minutesLeft(expires, expires + 60_000L))
    }

    @Test
    fun `the countdown wakes exactly when the minute shown changes`() {
        val expires = 1_000_000_000L
        // 9:30 left shows 10; 30 s later 9:00 left shows 9.
        var now = expires - 9 * 60_000L - 30_000L
        assertEquals(30_000L, PairingCode.millisToNextTick(expires, now))
        assertEquals(10, PairingCode.minutesLeft(expires, now))
        now += PairingCode.millisToNextTick(expires, now)
        assertEquals(9, PairingCode.minutesLeft(expires, now))
        // Exactly on a minute: a whole minute to the next one.
        assertEquals(60_000L, PairingCode.millisToNextTick(expires, now))
        // In the last minute the next tick is the expiry itself.
        now = expires - 20_000L
        now += PairingCode.millisToNextTick(expires, now)
        assertNull(PairingCode.minutesLeft(expires, now))
    }

    @Test
    fun `the countdown from a fresh code reaches the expiry in 15 ticks`() {
        val expires = 1_000_000_000L
        var now = expires - PairingCode.VALIDITY_MINUTES * 60_000L + 1_234L
        val shown = mutableListOf<Int>()
        while (now < expires) {
            shown += PairingCode.minutesLeft(expires, now)!!
            now += PairingCode.millisToNextTick(expires, now)
        }
        assertEquals((15 downTo 1).toList(), shown)
        assertEquals(expires, now)
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
