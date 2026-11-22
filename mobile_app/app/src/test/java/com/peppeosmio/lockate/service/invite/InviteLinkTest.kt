package com.peppeosmio.lockate.service.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    private val code = InviteCode.parse("42-acid-acorn-acre")!!

    @Test
    fun `a link reads back the server, the code and the API key`() {
        val link = InviteLink("https://lockate.example.com", code, "id:secret")
        val uri = link.toUri()

        assertEquals(
            "lockate://invite?server=https%3A%2F%2Flockate.example.com&code=42-acid-acorn-acre" +
                "&key=id%3Asecret",
            uri,
        )
        val parsed = InviteLink.parse(uri)!!
        assertEquals("https://lockate.example.com", parsed.server)
        assertEquals("42-acid-acorn-acre", parsed.code.toString())
        assertEquals("id:secret", parsed.apiKey)
    }

    @Test
    fun `a server without API keys has no key in the link`() {
        val uri = InviteLink("http://10.0.2.2:3118", code, null).toUri()

        assertEquals(false, uri.contains("key="))
        assertNull(InviteLink.parse(uri)!!.apiKey)
    }

    @Test
    fun `anything that isn't a Lockate invite is refused`() {
        assertNull(InviteLink.parse("https://example.com/?server=x&code=42-acid-acorn-acre"))
        assertNull(InviteLink.parse("lockate://invite?server=x&code=not-a-code"))
        assertNull(InviteLink.parse("lockate://invite?code=42-acid-acorn-acre"))
        assertNull(InviteLink.parse("just some text"))
    }
}
