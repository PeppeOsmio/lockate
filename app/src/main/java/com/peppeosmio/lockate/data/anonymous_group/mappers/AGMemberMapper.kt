package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.service.crypto.CryptoService
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
            id = entity.id,
            name = entity.name,
            createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                .toLocalDateTime(TimeZone.UTC),
            lastLocationRecord = entity.lastLocationRecordId?.let {
                LocationRecord(
                    id=entity.lastLocationRecordId,
                    coordinates = Coordinates(
                        latitude = entity.lastLatitude!!,
                        longitude = entity.lastLongitude!!

                    ),
                    timestamp = Instant.fromEpochMilliseconds(entity.lastSeen!!)
                        .toLocalDateTime(TimeZone.UTC)
                )
            })
    }

    @OptIn(ExperimentalTime::class)
    fun domainToEntity(
        agMember: AGMember, anonymousGroupId: String
    ): AGMemberEntity {
        return AGMemberEntity(
            agMember.id,
            name = agMember.name,
            createdAt = agMember.createdAt.toInstant(TimeZone.UTC)
                .toEpochMilliseconds(),
            lastLocationRecordId = agMember.lastLocationRecord?.id,
            lastLatitude = agMember.lastLocationRecord?.coordinates?.latitude,
            lastLongitude = agMember.lastLocationRecord?.coordinates?.longitude,
            lastSeen = agMember.lastLocationRecord?.timestamp?.toInstant(TimeZone.UTC)
                ?.toEpochMilliseconds(),
            anonymousGroupId = anonymousGroupId,
        )
    }

    suspend fun dtoToDomain(
        encryptedAGMemberDto: EncryptedAGMemberDto, cryptoService: CryptoService, key: ByteArray
    ): AGMember {
        val name = cryptoService.decrypt(
            encryptedData = EncryptedDataMapper.toDomain(encryptedAGMemberDto.encryptedName),
            key = key
        ).toString(Charsets.UTF_8)
        val lastLocation = encryptedAGMemberDto.encryptedLastLocationRecord?.let {
            LocationRecordMapper.toDomain(
                encryptedLocationRecordDto = it, cryptoService = cryptoService,
                key = key,
            )
        }
        return AGMember(
            id = encryptedAGMemberDto.id,
            name = name,
            createdAt = encryptedAGMemberDto.createdAt,
            lastLocationRecord = lastLocation
        )
    }
}