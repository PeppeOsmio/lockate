package com.peppeosmio.lockate.service.crypto

/** ISO/IEC 7816-4 padding: a 0x80 byte and zeros up to [size]. */
object Padding {
    private const val MARKER: Byte = 0x80.toByte()

    fun pad(data: ByteArray, size: Int): ByteArray {
        require(data.size < size) { "Payload of ${data.size} bytes doesn't fit in $size" }
        val padded = ByteArray(size)
        data.copyInto(padded)
        padded[data.size] = MARKER
        return padded
    }

    fun unpad(padded: ByteArray): ByteArray {
        var end = padded.size - 1
        while (end >= 0 && padded[end] == 0.toByte()) end--
        if (end < 0 || padded[end] != MARKER) throw CryptoException()
        return padded.copyOf(end)
    }
}
