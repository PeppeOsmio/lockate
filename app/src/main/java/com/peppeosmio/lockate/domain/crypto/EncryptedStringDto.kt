package com.peppeosmio.lockate.domain.crypto

import com.peppeosmio.lockate.service.crypto.EncryptedString
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
data class EncryptedStringDto(
    val cipherText: String,
    val iv: String,
    val authTag: String,
    val salt: String,
) {
    fun toEncryptedString(): EncryptedString {
        return EncryptedString(
            cipherText = Base64.decode(cipherText),
            iv = Base64.decode(iv),
            authTag = Base64.decode(authTag),
            salt = Base64.decode(salt)
        )
    }

    companion object {
        fun fromEncryptedString(encryptedString: EncryptedString): EncryptedStringDto {
            return EncryptedStringDto(
                cipherText = Base64.encode(encryptedString.cipherText),
                iv = Base64.encode(encryptedString.iv),
                authTag = Base64.encode(encryptedString.authTag),
                salt = Base64.encode(encryptedString.salt),
            )
        }
    }
}
