package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedDataDto
import kotlin.io.encoding.Base64

object EncryptedDataMapper {
    fun toDto(encryptedData: EncryptedData): EncryptedDataDto {
        return EncryptedDataDto(
            cipherText = Base64.encode(encryptedData.cipherText),
            iv = Base64.encode(encryptedData.iv)
        )
    }

    fun toDomain(encryptedDataDto: EncryptedDataDto): EncryptedData {
        return EncryptedData(
            cipherText = Base64.decode(encryptedDataDto.cipherText),
            iv = Base64.decode(encryptedDataDto.iv)
        )
    }
}