package com.peppeosmio.jpake

/** Length-prefixed encoding: the length as LEB128, then the bytes */
internal fun prependLen(data: ByteArray): ByteArray {
    var length = data.size
    val encodedLength = mutableListOf<Byte>()
    while (true) {
        if (length < 128) {
            encodedLength.add(length.toByte())
        } else {
            encodedLength.add(((length and 0x7f) or 0x80).toByte())
        }
        length = length shr 7
        if (length == 0) break
    }
    return encodedLength.toByteArray() + data
}

/** Concatenates the parts, each with its length in front, so they can be told apart again */
fun lvCat(vararg parts: ByteArray): ByteArray =
    parts.fold(ByteArray(0)) { acc, part -> acc + prependLen(part) }

/** Inverse of [lvCat]; null if the bytes aren't a well-formed sequence of length-prefixed fields */
fun lvSplit(data: ByteArray): List<ByteArray>? {
    val parts = mutableListOf<ByteArray>()
    var offset = 0
    while (offset < data.size) {
        var length = 0
        var shift = 0
        while (true) {
            if (offset >= data.size || shift > 28) return null
            val byte = data[offset++].toInt() and 0xff
            length = length or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        if (length > data.size - offset) return null
        parts.add(data.copyOfRange(offset, offset + length))
        offset += length
    }
    return parts
}

/** Compares without exiting early, so the time taken doesn't reveal where two tags differ */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var difference = 0
    for (i in a.indices) {
        difference = difference or (a[i].toInt() xor b[i].toInt())
    }
    return difference == 0
}
