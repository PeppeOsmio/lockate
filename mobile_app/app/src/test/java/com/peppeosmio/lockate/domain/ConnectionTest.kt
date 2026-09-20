package com.peppeosmio.lockate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTest {

    private fun connection(url: String) = Connection(
        id = null, name = "", url = url, apiKey = null, username = null, authToken = null
    )

    @Test
    fun `getWebSocketUrl converts https to wss`() {
        assertEquals("wss://example.com", connection("https://example.com").getWebSocketUrl())
    }

    @Test
    fun `getWebSocketUrl converts http to ws`() {
        assertEquals("ws://example.com", connection("http://example.com").getWebSocketUrl())
    }

    @Test
    fun `getWebSocketUrl prefixes a bare url with ws scheme`() {
        assertEquals("ws://example.com", connection("example.com").getWebSocketUrl())
    }
}
