package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.LocationRecord
import com.peppeosmio.lockate.exceptions.InvalidByteCoordinatesException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.utils.DoubleBytesUtils

object LocationRecordMapper {
    suspend fun toDomain(
        encryptedLocationRecordDto: EncryptedLocationRecordDto,
        cryptoService: CryptoService,
        key: ByteArray
    ): LocationRecord {
        val decryptedCoordinates = cryptoService.decrypt(
            encryptedData = EncryptedDataMapper.toDomain(encryptedLocationRecordDto.encryptedCoordinates),
            key = key
        )
        try {
            val latitude =
                DoubleBytesUtils.byteArrayToDouble(decryptedCoordinates.copyOfRange(0, 8))
            val longitude =
                DoubleBytesUtils.byteArrayToDouble(decryptedCoordinates.copyOfRange(8, 16))
            return LocationRecord(
                coordinates = Coordinates(latitude = latitude, longitude = longitude),
                timestamp = encryptedLocationRecordDto.timestamp
            )
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            throw InvalidByteCoordinatesException()
        }
    }

    suspend fun toDto(
        locationRecord: LocationRecord, cryptoService: CryptoService, key: ByteArray
    ): EncryptedLocationRecordDto {
        val encryptedCoordinates = cryptoService.encrypt(
            data = locationRecord.coordinates.toByteArray(),
            key = key,
        )
        return EncryptedLocationRecordDto(
            encryptedCoordinates = EncryptedDataMapper.toDto(encryptedCoordinates),
            timestamp = locationRecord.timestamp,
        )
    }
}