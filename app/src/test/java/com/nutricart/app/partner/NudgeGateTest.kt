package com.nutricart.app.partner

import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Website nudges are fetched (and so marked seen) only when they can be shown. The app-wide switch
 * and the Android 13 permission are one platform call; this is the channel half of the gate and
 * the gate itself.
 */
class NudgeGateTest {

    @Test
    fun `a channel the user switched off holds nudges back`() {
        assertFalse(PartnerInboxWorker.channelShows(NotificationManagerCompat.IMPORTANCE_NONE))
    }

    @Test
    fun `any other importance lets them through`() {
        assertTrue(PartnerInboxWorker.channelShows(NotificationManagerCompat.IMPORTANCE_HIGH))
        assertTrue(PartnerInboxWorker.channelShows(NotificationManagerCompat.IMPORTANCE_DEFAULT))
        assertTrue(PartnerInboxWorker.channelShows(NotificationManagerCompat.IMPORTANCE_MIN))
    }

    @Test
    fun `a channel that cannot be read does not hold them back`() {
        assertTrue(PartnerInboxWorker.channelShows(null))
    }

    @Test
    fun `nothing is fetched, so nothing is marked seen, while notifications cannot show`() = runBlocking {
        var fetches = 0
        val shown = PartnerInboxWorker.nudgesToShow(canNotify = false) { fetches++; listOf("Eat something") }
        assertEquals(0, fetches)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `when they can show, the nudges are fetched once and all shown`() = runBlocking {
        var fetches = 0
        val shown = PartnerInboxWorker.nudgesToShow(canNotify = true) { fetches++; listOf("Eat something", "Lunch?") }
        assertEquals(1, fetches)
        assertEquals(listOf("Eat something", "Lunch?"), shown)
    }
}
