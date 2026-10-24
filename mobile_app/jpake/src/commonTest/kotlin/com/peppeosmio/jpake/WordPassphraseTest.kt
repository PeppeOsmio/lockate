package com.peppeosmio.jpake

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordPassphraseTest {

    @Test
    fun generatedWordsComeFromTheDefaultWordlist() {
        repeat(100) {
            val words = WordPassphrase.generate()
            assertEquals(3, words.size)
            assertTrue(words.all { it in EFF_SHORT_WORDLIST })
        }
    }

    @Test
    fun theDefaultWordlistHasNoDashesOrDuplicates() {
        assertEquals(1295, EFF_SHORT_WORDLIST.size)
        assertEquals(EFF_SHORT_WORDLIST.size, EFF_SHORT_WORDLIST.toSet().size)
        assertTrue(EFF_SHORT_WORDLIST.all { word -> word.all { it in 'a'..'z' } })
    }

    @Test
    fun theCountAndTheWordlistAreConfigurable() {
        val wordlist = listOf("red", "green", "blue")
        val words = WordPassphrase.generate(count = 5, wordlist = wordlist)
        assertEquals(5, words.size)
        assertTrue(words.all { it in wordlist })
    }

    @Test
    fun generateRejectsAnEmptyWordlistOrACountBelowOne() {
        assertFailsWith<IllegalArgumentException> { WordPassphrase.generate(wordlist = emptyList()) }
        assertFailsWith<IllegalArgumentException> { WordPassphrase.generate(count = 0) }
    }

    @Test
    fun parsingAcceptsAnyCaseAndTheDefaultSeparators() {
        val expected = listOf("acid", "acorn", "acre")
        assertEquals(expected, WordPassphrase.parse("acid-acorn-acre"))
        assertEquals(expected, WordPassphrase.parse("  ACID acorn.Acre "))
        assertEquals(expected, WordPassphrase.parse("acid_acorn - acre"))
    }

    @Test
    fun parsingRejectsTheWrongCountUnknownWordsAndEmptyInput() {
        assertNull(WordPassphrase.parse("acid-acorn"))
        assertNull(WordPassphrase.parse("acid-acorn-acre-acted"))
        assertNull(WordPassphrase.parse("acid-acorn-zzzzz"))
        assertNull(WordPassphrase.parse(""))
    }

    @Test
    fun parsingFollowsTheConfiguredCountWordlistAndSeparators() {
        val wordlist = listOf("red", "green", "blue")
        assertEquals(
            listOf("red", "blue"),
            WordPassphrase.parse("red+blue", count = 2, wordlist = wordlist, separators = Regex("\\+"))
        )
        assertNull(WordPassphrase.parse("red-blue", count = 2, wordlist = wordlist, separators = Regex("\\+")))
    }

    @Test
    fun bytesJoinTheWordsWithTheSeparator() {
        assertContentEquals(
            "acid-acorn-acre".encodeToByteArray(), WordPassphrase.bytes(listOf("acid", "acorn", "acre"))
        )
        assertContentEquals(
            "a b".encodeToByteArray(), WordPassphrase.bytes(listOf("a", "b"), separator = " ")
        )
    }
}
