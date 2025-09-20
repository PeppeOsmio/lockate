package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.serialization.Serializable

@Serializable
data class AGLocationUpdateDto(
    val location: EncryptedAGLocationDto, val agMemberId: String
) {
    suspend fun toAGLocationUpdate(
        cryptoService: CryptoService, memberPassword: String
    ): AGLocationUpdate {
        return AGLocationUpdate(
            location = location.toDecrypted(
                cryptoService = cryptoService, memberPassword = memberPassword
            ), agMemberId = agMemberId
        )
    }
}
