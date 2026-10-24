package com.peppeosmio.lockate.service.invite

import com.peppeosmio.jpake.WordPassphrase

/**
 * An invite code such as "7-guitar-ocean-lamp". The number picks the server mailbox and isn't
 * secret; the words are the J-PAKE password (about 31 bits, one guess per invite).
 */
class InviteCode(val nameplate: Int, val words: List<String>) {

    companion object {
        const val WORD_COUNT = 3
        const val MAX_NAMEPLATE = 9999

        fun generateWords(): List<String> = WordPassphrase.generate(WORD_COUNT)

        /** Accepts any case and dashes, spaces or dots between the parts */
        fun parse(input: String): InviteCode? {
            val parts = input.trim().split(WordPassphrase.DEFAULT_SEPARATORS).filter { it.isNotEmpty() }
            if (parts.size != WORD_COUNT + 1) return null
            val nameplate = parts[0].toIntOrNull()?.takeIf { it in 1..MAX_NAMEPLATE } ?: return null
            val words = WordPassphrase.parse(parts.drop(1).joinToString(" "), WORD_COUNT) ?: return null
            return InviteCode(nameplate, words)
        }
    }

    override fun toString(): String = (listOf(nameplate.toString()) + words).joinToString("-")
}
