package com.nutricart.app.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * What a Settings action reports for an HTTP error. A 403 from PostgREST is a refusal (row-level
 * security or a grant), which signing in again would not change; GoTrue's 401 and 403 mean the
 * session itself is dead.
 */
class CloudResultTest {

    private fun error(code: Int, path: String) = HttpException(
        Response.error<Unit>(
            "{}".toResponseBody("application/json".toMediaType()),
            okhttp3.Response.Builder()
                .code(code).message("Error").protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://example.invalid/$path").build())
                .build(),
        )
    )

    @Test
    fun `a PostgREST 403 is a refusal, not a dead session`() {
        // "New code" against a database whose policies refuse the row (42501).
        assertEquals(CloudResult.Failed, cloudResultFor(error(403, "rest/v1/pairing_codes")))
        assertEquals(CloudResult.Failed, cloudResultFor(error(403, "rest/v1/partner_links?id=eq.l1")))
    }

    @Test
    fun `GoTrue's 403 is a dead session`() {
        // linkEmail's PUT with a revoked session or a deleted user.
        assertEquals(CloudResult.Auth, cloudResultFor(error(403, "auth/v1/user")))
        assertEquals(CloudResult.Auth, cloudResultFor(error(403, "auth/v1/signup")))
    }

    @Test
    fun `a 401 is a dead session from either`() {
        assertEquals(CloudResult.Auth, cloudResultFor(error(401, "rest/v1/pairing_codes")))
        assertEquals(CloudResult.Auth, cloudResultFor(error(401, "auth/v1/user")))
    }

    @Test
    fun `anything else is a failure`() {
        assertEquals(CloudResult.Failed, cloudResultFor(error(409, "rest/v1/pairing_codes")))
        assertEquals(CloudResult.Failed, cloudResultFor(error(422, "auth/v1/user")))
        assertEquals(CloudResult.Failed, cloudResultFor(error(500, "rest/v1/profiles")))
    }

    @Test
    fun `a 403 that does not say where it came from counts as a dead session, as before`() {
        // Response.error(code, body) alone puts http://localhost/ in the request.
        val bare = HttpException(Response.error<Unit>(403, "{}".toResponseBody("application/json".toMediaType())))
        assertEquals(CloudResult.Auth, cloudResultFor(bare))
    }
}
