package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedDataDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.service.crypto.CryptoService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.encoding.Base64

class LocationRecordMapperTest {

    private val key = "key".toByteArray()

    @Test
    fun `toDomain decrypts coordinates and builds a LocationRecord`() = runTest {
        val cryptoService = mockk<CryptoService>()
        val coordinates = Coordinates(latitude = 40.8517746, longitude = 14.2681244)
        val timestamp = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val encryptedDto = EncryptedLocationRecordDto(
            encryptedCoordinates = EncryptedDataDto(cipherText = "", iv = ""), timestamp = timestamp
        )
        coEvery { cryptoService.decrypt(any(), key) } returns coordinates.toByteArray()

        val result = LocationRecordMapper.toDomain(encryptedDto, cryptoService, key)

        assertEquals(LocationRecord(coordinates = coordinates, timestamp = timestamp), result)
    }

    @Test
    fun `toDto encrypts the coordinates and preserves the timestamp`() = runTest {
        val cryptoService = mockk<CryptoService>()
        val coordinates = Coordinates(latitude = 40.8517746, longitude = 14.2681244)
        val timestamp = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val locationRecord = LocationRecord(coordinates = coordinates, timestamp = timestamp)
        val encrypted = EncryptedData(cipherText = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6))
        coEvery { cryptoService.encrypt(coordinates.toByteArray(), key) } returns encrypted

        val dto = LocationRecordMapper.toDto(locationRecord, cryptoService, key)

        assertEquals(Base64.encode(encrypted.cipherText), dto.encryptedCoordinates.cipherText)
        assertEquals(Base64.encode(encrypted.iv), dto.encryptedCoordinates.iv)
        assertEquals(timestamp, dto.timestamp)
    }
}
