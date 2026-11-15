package com.peppeosmio.lockate.service.version

import com.peppeosmio.lockate.data.version.ServerVersionDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiCompatibilityTest {

    @Test
    fun `a server serving our API is compatible`() {
        assertEquals(ApiCompatibility.Compatible, apiCompatibility(listOf(APP_API)))
        assertEquals(ApiCompatibility.Compatible, apiCompatibility(listOf(APP_API, APP_API + 1)))
    }

    @Test
    fun `a server without the version endpoint is older than 1_0`() {
        assertEquals(ApiCompatibility.ServerTooOld, apiCompatibility(null))
    }

    @Test
    fun `a server serving only older APIs needs an update`() {
        assertEquals(ApiCompatibility.ServerTooOld, apiCompatibility(listOf(APP_API - 1)))
    }

    @Test
    fun `a server serving only newer APIs needs a newer app`() {
        assertEquals(ApiCompatibility.AppTooOld, apiCompatibility(listOf(APP_API + 1)))
    }

    @Test
    fun `the server list shows the version and what to update`() {
        assertEquals(
            "Server 1.2.0",
            serverVersionLabel(ServerVersionDto("1.2.0", listOf(APP_API))),
        )
        assertEquals("Server needs an update", serverVersionLabel(null))
        assertEquals(
            "Server 2.0.0, needs a newer Lockate",
            serverVersionLabel(ServerVersionDto("2.0.0", listOf(APP_API + 1))),
        )
    }

    @Test
    fun `only an incompatible server is refused, saying what to update`() {
        assertEquals(null, incompatibilityMessage(ApiCompatibility.Compatible))
        assertEquals(
            "This server needs an update to work with this Lockate",
            incompatibilityMessage(ApiCompatibility.ServerTooOld),
        )
        assertEquals(
            "This server needs a newer version of Lockate",
            incompatibilityMessage(ApiCompatibility.AppTooOld),
        )
    }
}
