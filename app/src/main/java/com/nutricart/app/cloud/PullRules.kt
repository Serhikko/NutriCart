package com.nutricart.app.cloud

import java.time.OffsetDateTime

/** What the phone does with one pulled row. */
sealed interface PullAction {
    /** Nothing to do: the phone's own live row, or a row the outbox is about to overwrite. */
    data object Skip : PullAction
    /** The website deleted a row this phone logged: remove the local row by its Room id. */
    data class DeleteLocal(val localId: Long) : PullAction
    /** The website deleted one of its own rows the phone had mirrored. */
    data class DeleteForeign(val cloudId: String) : PullAction
    /** A live row another client wrote: insert it, or update the local copy. */
    data class UpsertForeign(val cloudId: String) : PullAction
}

/**
 * The conflict rules of the pull, with no I/O so they can be tested. The
 * phone stays the author of its own rows: a row this install minted is only
 * ever DELETED by a pull (the website can remove it), never edited. Rows the
 * website minted are applied as they come. A row whose id still sits in the
 * outbox is skipped: the phone's newer write is on its way up and would be
 * undone by applying the older server copy.
 */
object PullRules {

    fun forRow(deviceId: String, cloudId: String, deleted: Boolean, pendingIds: Set<String>): PullAction {
        if (cloudId in pendingIds) return PullAction.Skip
        val localId = CloudRows.localIdOf(deviceId, cloudId)
        return when {
            CloudRows.isOwnId(deviceId, cloudId) ->
                if (deleted && localId != null) PullAction.DeleteLocal(localId) else PullAction.Skip
            deleted -> PullAction.DeleteForeign(cloudId)
            else -> PullAction.UpsertForeign(cloudId)
        }
    }

    /**
     * Weight has no per-row id (one row per day and source), so the server
     * copy is applied whenever it differs from the local one and the phone has
     * no newer value waiting in the outbox for that key.
     */
    fun applyWeight(pendingKey: String, pendingKeys: Set<String>, localKg: Double?, remoteKg: Double): Boolean =
        pendingKey !in pendingKeys && localKg != remoteKg

    /**
     * The `updated_at` a pull asks past. updated_at is the writing transaction's start time, and a
     * transaction that started first can commit after a later one the pull has already seen, so the
     * pull asks again from [OVERLAP_SECONDS] before the watermark (which itself stays the true
     * newest, see [newest]). Re-applying a row is idempotent: the same insert-or-update, the same
     * delete. A watermark that does not parse is used as it is; none means everything.
     */
    fun since(watermark: String?): String {
        if (watermark == null) return EPOCH
        return try {
            OffsetDateTime.parse(watermark).toInstant().minusSeconds(OVERLAP_SECONDS).toString()
        } catch (e: java.time.format.DateTimeParseException) {
            watermark
        }
    }

    const val OVERLAP_SECONDS = 60L
    const val EPOCH = "1970-01-01T00:00:00Z"

    /**
     * A PostgREST `or` filter for every row [forRow] can act on: any tombstone, and any row another
     * client minted. It leaves out this install's own live rows, which [forRow] always skips;
     * without it the overlap would bring the whole backfill (written within seconds) down again on
     * every pull until a newer row arrived. The device id is 8 hex characters: nothing in it means
     * anything to LIKE or to PostgREST's filter syntax, and `*` is PostgREST's `%`.
     */
    fun actionable(deviceId: String): String = "(deleted_at.not.is.null,id.not.like.$deviceId:*)"

    /** The later of two PostgREST timestamps; falls back to text order if one does not parse. */
    fun newest(a: String?, b: String): String {
        if (a == null) return b
        return try {
            if (OffsetDateTime.parse(b).isAfter(OffsetDateTime.parse(a))) b else a
        } catch (e: java.time.format.DateTimeParseException) {
            if (b > a) b else a
        }
    }
}
