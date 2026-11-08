package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedLocationRecordDto
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedMemberMessage
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec

object LocationRecordMapper {
    suspend fun toDomain(
        encryptedLocationRecordDto: EncryptedLocationRecordDto,
        payloadCodec: PayloadCodec,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): LocationRecord {
        val message =
            payloadCodec.decryptMemberMessage(
                message =
                    EncryptedMemberMessage(
                        coordinates =
                            EncryptedDataMapper.toDomain(
                                encryptedLocationRecordDto.encryptedCoordinates
                            ),
                        activity =
                            EncryptedDataMapper.toDomain(
                                encryptedLocationRecordDto.encryptedActivity
                            ),
                    ),
                key = key,
                groupId = groupId,
                memberId = memberId,
            )
        return LocationRecord(
            coordinates = message.coordinates,
            timestamp = encryptedLocationRecordDto.timestamp,
            activity = message.activity,
        )
    }
}
