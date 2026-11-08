package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.service.crypto.CryptoException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapDecryptableTest {

    private fun decrypt(item: String): String =
        if (item.startsWith("bad")) throw CryptoException() else item.uppercase()

    @Test
    fun `elements that can't be decrypted are skipped and reported`() = runTest {
        val skipped = mutableListOf<String>()

        val result =
            listOf("a", "bad1", "b", "bad2").mapDecryptable(onUndecryptable = { skipped += it }) {
                decrypt(it)
            }

        assertEquals(listOf("A", "B"), result)
        assertEquals(listOf("bad1", "bad2"), skipped)
    }

    @Test
    fun `a list with nothing to skip maps as usual`() = runTest {
        val skipped = mutableListOf<String>()

        val result =
            listOf("a", "b").mapDecryptable(onUndecryptable = { skipped += it }) {
                decrypt(it)
            }

        assertEquals(listOf("A", "B"), result)
        assertEquals(emptyList<String>(), skipped)
    }

    @Test
    fun `other failures are not swallowed`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                listOf("a").mapDecryptable(onUndecryptable = {}) { error("boom") }
            }
        }
    }
}
