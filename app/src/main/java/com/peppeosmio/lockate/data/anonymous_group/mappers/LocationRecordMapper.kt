package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
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

            return LocationRecord(
                id = encryptedLocationRecordDto.id,
                coordinates = Coordinates.fromByteArray(decryptedCoordinates),
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
            id = locationRecord.id,
            encryptedCoordinates = EncryptedDataMapper.toDto(encryptedCoordinates),
            timestamp = locationRecord.timestamp,
        )
    }
}