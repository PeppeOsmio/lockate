package com.peppeosmio.lockate.service.crypto

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlin.io.encoding.Base64

data class EncryptedString(
    val cipherText: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray,
    val salt: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedString

        if (!cipherText.contentEquals(other.cipherText)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }
}
