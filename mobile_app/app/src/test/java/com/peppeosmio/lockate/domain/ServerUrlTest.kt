package com.peppeosmio.lockate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test
    fun `scheme and host are lower case`() {
        assertEquals(
            "https://lockate.example.com",
            ServerUrl.normalize("HTTPS://Lockate.Example.com"),
        )
    }

    @Test
    fun `the default port and the trailing slash go`() {
        assertEquals(
            "https://lockate.example.com",
            ServerUrl.normalize("https://lockate.example.com:443/"),
        )
        assertEquals("http://192.168.1.20", ServerUrl.normalize("http://192.168.1.20:80"))
    }

    @Test
    fun `another port and a path stay`() {
        assertEquals("http://10.0.2.2:3118", ServerUrl.normalize(" http://10.0.2.2:3118/ "))
        assertEquals(
            "https://example.com/lockate",
            ServerUrl.normalize("https://example.com/lockate/"),
        )
    }

    @Test
    fun `the same server written two ways is one address`() {
        assertEquals(
            ServerUrl.normalize("https://Lockate.example.com/"),
            ServerUrl.normalize("https://lockate.example.com:443"),
        )
    }

    @Test
    fun `the host names a server`() {
        assertEquals("lockate.example.com", ServerUrl.host("https://Lockate.example.com:8443/x"))
        assertNull(ServerUrl.host("not a url"))
    }
}
