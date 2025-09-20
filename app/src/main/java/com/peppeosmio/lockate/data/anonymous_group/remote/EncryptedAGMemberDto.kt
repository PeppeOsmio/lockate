package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.anonymous_group.AGLocation
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.crypto.EncryptedString
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets

@Serializable
data class EncryptedAGMemberDto(
    val id: String,
    val encryptedName: EncryptedStringDto,
    val createdAt: LocalDateTime,
    val encryptedLastLocation: EncryptedAGLocationDto?
) {
    suspend fun toDecrypted(
        cryptoService: CryptoService, memberPassword: String
    ): AGMember {
        return AGMember(
            id = id,
            name = cryptoService.aesGcmDecrypt(
                encryptedString = encryptedName.toEncryptedString(),
                passwordBytes = memberPassword.toByteArray(
                    StandardCharsets.UTF_8
                )
            ).toString(Charsets.UTF_8),
            createdAt = createdAt,
            lastLocation = encryptedLastLocation?.toDecrypted(
                cryptoService = cryptoService,
                memberPassword = memberPassword
            )
        )
    }
}
