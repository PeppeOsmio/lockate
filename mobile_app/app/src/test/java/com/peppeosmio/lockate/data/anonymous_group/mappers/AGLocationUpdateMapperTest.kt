package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedDataDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.data.anonymous_group.remote.LocationUpdateDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.service.crypto.CryptoService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AGLocationUpdateMapperTest {

    @Test
    fun `toDomain decrypts the location and preserves the member id`() = runTest {
        val cryptoService = mockk<CryptoService>()
        val key = "key".toByteArray()
        val coordinates = Coordinates(latitude = 40.8517746, longitude = 14.2681244)
        val timestamp = LocalDateTime(2026, 7, 12, 14, 30, 5)
        coEvery { cryptoService.decrypt(any(), key) } returns coordinates.toByteArray()
        val dto = LocationUpdateDto(
            location = EncryptedLocationRecordDto(
                encryptedCoordinates = EncryptedDataDto(cipherText = "", iv = ""),
                timestamp = timestamp
            ), memberId = "member-1"
        )

        val result = AGLocationUpdateMapper.toDomain(dto, cryptoService, key)

        assertEquals("member-1", result.agMemberId)
        assertEquals(coordinates, result.locationRecord.coordinates)
        assertEquals(timestamp, result.locationRecord.timestamp)
    }
}
