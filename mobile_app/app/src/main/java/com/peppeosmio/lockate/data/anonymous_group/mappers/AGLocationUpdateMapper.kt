package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.LocationUpdateDto
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.service.crypto.CryptoService

object AGLocationUpdateMapper {
    suspend fun toDomain(
        locationUpdateDto: LocationUpdateDto, cryptoService: CryptoService, key: ByteArray
    ): AGLocationUpdate {
        return AGLocationUpdate(
            locationRecord = LocationRecordMapper.toDomain(
                encryptedLocationRecordDto = locationUpdateDto.location,
                cryptoService = cryptoService,
                key = key
            ), agMemberId = locationUpdateDto.memberId
        )
    }
}