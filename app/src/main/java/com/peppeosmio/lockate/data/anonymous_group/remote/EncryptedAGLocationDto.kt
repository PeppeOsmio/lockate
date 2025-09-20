package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.anonymous_group.AGLocation
import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import com.peppeosmio.lockate.exceptions.InvalidByteCoordinatesException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.location.Location
import com.peppeosmio.lockate.utils.DoubleBytesUtils
import dev.whyoleg.cryptography.bigint.decodeToBigInt
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedAGLocationDto(
    val encryptedCoordinates: EncryptedStringDto,
    val timestamp: LocalDateTime,
) {
    suspend fun toDecrypted(cryptoService: CryptoService, memberPassword: String): AGLocation {
        val decryptedCoordinates = cryptoService.aesGcmDecrypt(
            encryptedString = encryptedCoordinates.toEncryptedString(),
            passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
        )
        try {
            val latitude =
                DoubleBytesUtils.byteArrayToDouble(decryptedCoordinates.copyOfRange(0, 8))
            val longitude =
                DoubleBytesUtils.byteArrayToDouble(decryptedCoordinates.copyOfRange(8, 16))
            return AGLocation(
                coordinates = Location(latitude = latitude, longitude = longitude),
                timestamp = timestamp
            )
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            throw InvalidByteCoordinatesException()
        }
    }
}
