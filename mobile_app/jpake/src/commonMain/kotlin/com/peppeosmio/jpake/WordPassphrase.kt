package com.peppeosmio.jpake

import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Short human-readable passwords made of words, e.g. "acid-acorn-acre", which are easy to read out
 * loud. With the default list, 3 words are about 31 bits: fine for J-PAKE, which allows one guess
 * per exchange, but not for anything an attacker can test offline.
 */
object WordPassphrase {
    const val DEFAULT_WORD_COUNT = 3
    val DEFAULT_SEPARATORS = Regex("[\\s.\\-_]+")
    const val DEFAULT_JOINER = "-"

    fun generate(
        count: Int = DEFAULT_WORD_COUNT, wordlist: List<String> = EFF_SHORT_WORDLIST
    ): List<String> {
        require(count >= 1) { "count must be at least 1" }
        require(wordlist.isNotEmpty()) { "wordlist is empty" }
        return List(count) { wordlist[CryptographyRandom.nextInt(wordlist.size)] }
    }

    /**
     * What a person typed, as the words, or null if it isn't [count] words of the [wordlist]. Any
     * case is accepted, and [separators] between the words.
     */
    fun parse(
        input: String,
        count: Int = DEFAULT_WORD_COUNT,
        wordlist: List<String> = EFF_SHORT_WORDLIST,
        separators: Regex = DEFAULT_SEPARATORS,
    ): List<String>? {
        val words = input.trim().lowercase().split(separators).filter { it.isNotEmpty() }
        if (words.size != count || words.any { it !in wordlist }) return null
        return words
    }

    /** The password bytes for the words, to give to [JPake] */
    fun bytes(words: List<String>, separator: String = DEFAULT_JOINER): ByteArray =
        words.joinToString(separator).encodeToByteArray()
}
