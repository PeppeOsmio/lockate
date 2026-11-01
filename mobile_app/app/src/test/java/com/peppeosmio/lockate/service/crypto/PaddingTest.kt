package com.peppeosmio.lockate.service.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PaddingTest {

    @Test
    fun `padding always gives the requested size`() {
        assertEquals(32, Padding.pad(ByteArray(0), 32).size)
        assertEquals(32, Padding.pad(ByteArray(31), 32).size)
    }

    @Test
    fun `unpad returns the original data`() {
        val data = "hello".toByteArray()

        assertArrayEquals(data, Padding.unpad(Padding.pad(data, 64)))
    }

    @Test
    fun `data ending in zeros or the marker survives`() {
        val data = byteArrayOf(1, 0, 0, 0x80.toByte(), 0, 0)

        assertArrayEquals(data, Padding.unpad(Padding.pad(data, 16)))
    }

    @Test
    fun `empty data survives`() {
        assertArrayEquals(ByteArray(0), Padding.unpad(Padding.pad(ByteArray(0), 16)))
    }

    @Test
    fun `data that doesn't fit is refused`() {
        try {
            Padding.pad(ByteArray(32), 32)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `unpad of something not padded throws CryptoException`() {
        try {
            Padding.unpad(ByteArray(16))
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }
}
