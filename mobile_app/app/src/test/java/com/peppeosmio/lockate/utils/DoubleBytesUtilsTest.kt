package com.peppeosmio.lockate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBytesUtilsTest {

    private val values = listOf(
        0.0,
        -0.0,
        1.0,
        -1.0,
        40.8517746,
        -14.2681244,
        Double.MIN_VALUE,
        Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
    )

    @Test
    fun `round trip preserves the original value, big endian`() {
        for (value in values) {
            val bytes = DoubleBytesUtils.doubleToByteArray(value, bigEndian = true)
            val roundTripped = DoubleBytesUtils.byteArrayToDouble(bytes, bigEndian = true)
            assertTrue(
                "expected $value but got $roundTripped",
                value.toRawBits() == roundTripped.toRawBits()
            )
        }
    }

    @Test
    fun `round trip preserves the original value, little endian`() {
        for (value in values) {
            val bytes = DoubleBytesUtils.doubleToByteArray(value, bigEndian = false)
            val roundTripped = DoubleBytesUtils.byteArrayToDouble(bytes, bigEndian = false)
            assertTrue(
                "expected $value but got $roundTripped",
                value.toRawBits() == roundTripped.toRawBits()
            )
        }
    }

    @Test
    fun `big endian and little endian produce reversed byte order`() {
        val bigEndianBytes = DoubleBytesUtils.doubleToByteArray(40.8517746, bigEndian = true)
        val littleEndianBytes = DoubleBytesUtils.doubleToByteArray(40.8517746, bigEndian = false)

        assertEquals(bigEndianBytes.toList(), littleEndianBytes.reversed())
    }

    @Test
    fun `byteArrayToDouble throws IllegalArgumentException for a 7 byte array`() {
        assertThrows(IllegalArgumentException::class.java) {
            DoubleBytesUtils.byteArrayToDouble(ByteArray(7))
        }
    }

    @Test
    fun `byteArrayToDouble throws IllegalArgumentException for a 9 byte array`() {
        assertThrows(IllegalArgumentException::class.java) {
            DoubleBytesUtils.byteArrayToDouble(ByteArray(9))
        }
    }
}
