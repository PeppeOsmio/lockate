package com.peppeosmio.lockate.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingEndpointTest {

    @Test
    fun `a 404 is a missing endpoint`() {
        assertTrue(isMissingEndpoint(404, """{"error":"not_found"}"""))
    }

    @Test
    fun `an older server's 500 for an unknown path is a missing endpoint`() {
        assertTrue(
            isMissingEndpoint(
                500,
                """{"error":"No static resource api/version for request '/api/version'."}""",
            )
        )
    }

    @Test
    fun `any other failure is not`() {
        assertFalse(isMissingEndpoint(500, """{"error":"Connection refused"}"""))
        assertFalse(isMissingEndpoint(502, ""))
    }
}
