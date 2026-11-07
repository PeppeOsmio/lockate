package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object AGMemberMapper {
    @OptIn(ExperimentalTime::class)
    fun entityToDomain(
        entity: AGMemberEntity,
    ): AGMember {
        return AGMember(
            internalId = entity.internalId,
            id = entity.id,
            name = entity.name,
            createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                .toLocalDateTime(TimeZone.UTC),
            lastLocationRecord = entity.lastLatitude?.let {
                LocationRecord(
                    coordinates = Coordinates(
                        latitude = it,
                        longitude = entity.lastLongitude!!

                    ),
                    timestamp = Instant.fromEpochMilliseconds(entity.lastSeen!!)
                        .toLocalDateTime(TimeZone.UTC),
                    activity = UserActivity.valueOf(entity.lastActivity)
                )
            },
            isAGAdmin = entity.isAGAdmin)
    }

    @OptIn(ExperimentalTime::class)
    fun domainToEntity(
        agMember: AGMember, anonymousGroupInternalId: Long
    ): AGMemberEntity {
        return AGMemberEntity(
            internalId = agMember.internalId,
            id = agMember.id,
            name = agMember.name,
            createdAt = agMember.createdAt.toInstant(TimeZone.UTC)
                .toEpochMilliseconds(),
            lastLatitude = agMember.lastLocationRecord?.coordinates?.latitude,
            lastLongitude = agMember.lastLocationRecord?.coordinates?.longitude,
            lastSeen = agMember.lastLocationRecord?.timestamp?.toInstant(TimeZone.UTC)
                ?.toEpochMilliseconds(),
            lastActivity = (agMember.lastLocationRecord?.activity ?: UserActivity.Unknown).name,
            anonymousGroupInternalId = anonymousGroupInternalId,
            isAGAdmin = agMember.isAGAdmin
        )
    }

    suspend fun dtoToDomain(
        encryptedAGMemberDto: EncryptedAGMemberDto,
        payloadCodec: PayloadCodec,
        key: ByteArray,
        groupId: String
    ): AGMember {
        val name = payloadCodec.decryptMemberName(
            EncryptedDataMapper.toDomain(encryptedAGMemberDto.encryptedName),
            key,
            groupId,
            encryptedAGMemberDto.id
        )
        val lastLocation = encryptedAGMemberDto.encryptedLastLocationRecord?.let {
            LocationRecordMapper.toDomain(
                encryptedLocationRecordDto = it,
                payloadCodec = payloadCodec,
                key = key,
                groupId = groupId,
                memberId = encryptedAGMemberDto.id
            )
        }
        return AGMember(
            internalId = 0,
            id = encryptedAGMemberDto.id,
            name = name,
            createdAt = encryptedAGMemberDto.createdAt,
            lastLocationRecord = lastLocation,
            isAGAdmin = encryptedAGMemberDto.isAGAdmin
        )
    }
}