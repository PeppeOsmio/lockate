package com.peppeosmio.jpake

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncodingTest {

    @Test
    fun lvSplitReversesLvCat() {
        val parts = listOf(ByteArray(0), "id".encodeToByteArray(), ByteArray(300) { it.toByte() })
        val split = lvSplit(lvCat(*parts.toTypedArray()))!!
        assertEquals(parts.size, split.size)
        parts.zip(split).forEach { (expected, actual) -> assertContentEquals(expected, actual) }
    }

    @Test
    fun lvSplitRejectsTruncatedInput() {
        val encoded = lvCat("hello".encodeToByteArray())
        assertNull(lvSplit(encoded.copyOf(encoded.size - 1)))
        assertNull(lvSplit(byteArrayOf(0x80.toByte())))
    }

    @Test
    fun constantTimeEqualsComparesContent() {
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }
}
