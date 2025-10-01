package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets

@Serializable
data class EncryptedAGMemberDto(
    val id: String,
    val encryptedName: EncryptedDataDto,
    val createdAt: LocalDateTime,
    val encryptedLastLocationRecord: EncryptedLocationRecordDto?
) {
//    suspend fun toDecrypted(
//        cryptoService: CryptoService, memberPassword: String
//    ): AGMember {
//        return AGMember(
//            id = id,
//            name = cryptoService.decrypt(
//                encryptedData = EncryptedDataMapper.toDomain(encryptedName),
//                passwordBytes = memberPassword.toByteArray(
//                    StandardCharsets.UTF_8
//                )
//            ).toString(Charsets.UTF_8),
//            createdAt = createdAt,
//            lastLocationRecord = encryptedLastLocationRecord?.toDecrypted(
//                cryptoService = cryptoService,
//                memberPassword = memberPassword
//            )
//        )
//    }
}
