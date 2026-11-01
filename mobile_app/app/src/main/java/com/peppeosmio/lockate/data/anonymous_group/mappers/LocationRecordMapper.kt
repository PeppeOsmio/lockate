package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec

object LocationRecordMapper {
    suspend fun toDomain(
        encryptedLocationRecordDto: EncryptedLocationRecordDto,
        payloadCodec: PayloadCodec,
        key: ByteArray,
        groupId: String,
        memberId: String
    ): LocationRecord {
        val message = payloadCodec.decryptMemberMessage(
            data = EncryptedDataMapper.toDomain(encryptedLocationRecordDto.encryptedPayload),
            key = key,
            groupId = groupId,
            memberId = memberId
        )
        return LocationRecord(
            coordinates = message.coordinates,
            timestamp = encryptedLocationRecordDto.timestamp,
            activity = message.activity
        )
    }
}
