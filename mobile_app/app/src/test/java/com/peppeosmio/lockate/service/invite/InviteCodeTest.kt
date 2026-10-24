package com.peppeosmio.lockate.service.invite

import com.peppeosmio.jpake.EFF_SHORT_WORDLIST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {

    @Test
    fun `generated words come from the wordlist`() {
        repeat(100) {
            val words = InviteCode.generateWords()
            assertEquals(InviteCode.WORD_COUNT, words.size)
            assertTrue(words.all { it in EFF_SHORT_WORDLIST })
        }
    }

    @Test
    fun `formatting and parsing round trip`() {
        val code = InviteCode(7, listOf("acid", "acorn", "acre"))
        assertEquals("7-acid-acorn-acre", code.toString())
        val parsed = InviteCode.parse(code.toString())!!
        assertEquals(7, parsed.nameplate)
        assertEquals(code.words, parsed.words)
    }

    @Test
    fun `parsing is lenient about case and separators`() {
        val parsed = InviteCode.parse("  7 Acid ACORN.acre ")!!
        assertEquals(7, parsed.nameplate)
        assertEquals(listOf("acid", "acorn", "acre"), parsed.words)
    }

    @Test
    fun `invalid codes are rejected`() {
        assertNull(InviteCode.parse("7-acid-acorn"))
        assertNull(InviteCode.parse("7-acid-acorn-acre-acid"))
        assertNull(InviteCode.parse("0-acid-acorn-acre"))
        assertNull(InviteCode.parse("10000-acid-acorn-acre"))
        assertNull(InviteCode.parse("seven-acid-acorn-acre"))
        assertNull(InviteCode.parse("7-acid-acorn-notaword"))
        assertNull(InviteCode.parse(""))
    }
}
