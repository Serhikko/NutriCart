package com.nutricart.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conflict rules of the pull: the phone owns its rows, the website owns its own. */
class PullRulesTest {

    private val device = "ab12cd34"

    @Test
    fun `a live row this phone logged is left alone`() {
        assertEquals(PullAction.Skip, PullRules.forRow(device, "ab12cd34:f:42", deleted = false, pendingIds = emptySet()))
    }

    @Test
    fun `a row this phone logged and the website deleted is removed locally by its Room id`() {
        assertEquals(PullAction.DeleteLocal(42), PullRules.forRow(device, "ab12cd34:f:42", deleted = true, pendingIds = emptySet()))
        assertEquals(PullAction.DeleteLocal(7), PullRules.forRow(device, "ab12cd34:w:7", deleted = true, pendingIds = emptySet()))
    }

    @Test
    fun `a website row is applied, its tombstone removes the mirrored copy`() {
        val id = "web:f:0b5c3f8e-3f8e-4c9a-9e1a-1234567890ab"
        assertEquals(PullAction.UpsertForeign(id), PullRules.forRow(device, id, deleted = false, pendingIds = emptySet()))
        assertEquals(PullAction.DeleteForeign(id), PullRules.forRow(device, id, deleted = true, pendingIds = emptySet()))
    }

    @Test
    fun `another phone's rows count as foreign, not as ours`() {
        assertEquals(PullAction.UpsertForeign("ffee0011:f:3"), PullRules.forRow(device, "ffee0011:f:3", deleted = false, pendingIds = emptySet()))
    }

    @Test
    fun `a row still waiting in the outbox is never overwritten by the server copy`() {
        val pending = setOf("web:f:abc", "ab12cd34:f:42")
        assertEquals(PullAction.Skip, PullRules.forRow(device, "web:f:abc", deleted = true, pendingIds = pending))
        assertEquals(PullAction.Skip, PullRules.forRow(device, "ab12cd34:f:42", deleted = true, pendingIds = pending))
    }

    @Test
    fun `a malformed own id is skipped rather than deleting the wrong row`() {
        assertEquals(PullAction.Skip, PullRules.forRow(device, "ab12cd34:f:not-a-number", deleted = true, pendingIds = emptySet()))
    }

    @Test
    fun `weight applies only when it differs and nothing newer is queued`() {
        assertTrue(PullRules.applyWeight("20724:MANUAL", emptySet(), localKg = 80.0, remoteKg = 79.5))
        assertTrue(PullRules.applyWeight("20724:MANUAL", emptySet(), localKg = null, remoteKg = 79.5))
        assertFalse(PullRules.applyWeight("20724:MANUAL", emptySet(), localKg = 79.5, remoteKg = 79.5))
        assertFalse(PullRules.applyWeight("20724:MANUAL", setOf("20724:MANUAL"), localKg = 80.0, remoteKg = 79.5))
    }

    @Test
    fun `the watermark keeps the later timestamp whatever the offset notation`() {
        assertEquals("2026-09-28T16:03:44.123456+00:00", PullRules.newest(null, "2026-09-28T16:03:44.123456+00:00"))
        assertEquals("2026-09-28T16:03:44.123456+00:00", PullRules.newest("2026-09-28T15:00:00+00:00", "2026-09-28T16:03:44.123456+00:00"))
        assertEquals("2026-09-28T18:00:00+02:00", PullRules.newest("2026-09-28T18:00:00+02:00", "2026-09-28T15:59:00+00:00"))
        // Text order as the fallback for something that is not a timestamp.
        assertEquals("b", PullRules.newest("a", "b"))
    }

    @Test
    fun `a pull asks again from a minute before the watermark`() {
        assertEquals("2026-09-28T16:02:44.123456Z", PullRules.since("2026-09-28T16:03:44.123456+00:00"))
        // Whatever the offset notation, the overlap is an instant a minute earlier.
        assertEquals("2026-09-28T15:59:00Z", PullRules.since("2026-09-28T18:00:00+02:00"))
        assertEquals("2026-09-28T23:59:30Z", PullRules.since("2026-09-29T00:00:30Z"))
    }

    @Test
    fun `the overlap is behind the watermark the pull keeps`() {
        val watermark = PullRules.newest("2026-09-28T15:00:00+00:00", "2026-09-28T16:03:44.123456+00:00")
        val since = java.time.Instant.parse(PullRules.since(watermark))
        val kept = java.time.OffsetDateTime.parse(watermark).toInstant()
        assertEquals(PullRules.OVERLAP_SECONDS, java.time.Duration.between(since, kept).seconds)
    }

    @Test
    fun `no watermark asks for everything, an unreadable one is used as it is`() {
        assertEquals(PullRules.EPOCH, PullRules.since(null))
        assertEquals("not-a-time", PullRules.since("not-a-time"))
    }

    @Test
    fun `the server leaves out only this phone's live rows, which the pull skips anyway`() {
        assertEquals("(deleted_at.not.is.null,id.not.like.ab12cd34:*)", PullRules.actionable(device))
        // What that filter keeps, row by row, against what forRow does with the row (nothing pending).
        val prefix = PullRules.actionable(device).substringAfter("id.not.like.").substringBefore("*")
        listOf("ab12cd34:f:42", "ab12cd34:w:7", "web:abc", "ff00ee11:f:42").forEach { id ->
            listOf(false, true).forEach { deleted ->
                val kept = deleted || !id.startsWith(prefix)
                val acted = PullRules.forRow(device, id, deleted, pendingIds = emptySet()) != PullAction.Skip
                assertEquals("$id deleted=$deleted", acted, kept)
            }
        }
    }
}
