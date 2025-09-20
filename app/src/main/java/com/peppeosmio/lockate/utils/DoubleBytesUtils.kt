package com.peppeosmio.lockate.utils

object DoubleBytesUtils {
    fun doubleToByteArray(value: Double, bigEndian: Boolean = true): ByteArray {
        val bits = value.toBits() // IEEE 754 representation as Long
        val result = ByteArray(8) { i ->
            // shift 8 bits to the right then keep only the last 8 bits
            ((bits ushr (i * 8)) and 0xFF).toByte()
        }
        if (bigEndian) {
            result.reverse()
        }
        return result
    }

    @Throws(IllegalArgumentException::class)
    fun byteArrayToDouble(bytes: ByteArray, bigEndian: Boolean = true): Double {
        if (bytes.size != 8) {
            throw IllegalArgumentException("Byte array must be exactly 8 bytes long")
        }
        var bits = 0L
        for (i in 0..7) {
            val bitsUnsigned = bytes[i].toLong() and 0xFF
            val leftShift = if (bigEndian) {
                (7 - i) * 8
            } else {
                i * 8
            }
            bits = bits or (bitsUnsigned shl leftShift)
        }
        return Double.fromBits(bits)
    }
}