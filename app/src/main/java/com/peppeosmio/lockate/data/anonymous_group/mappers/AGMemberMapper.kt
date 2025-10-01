package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

object AGMemberMapper {
    @OptIn(ExperimentalTime::class)
    fun toEntity(
        agMember: AGMember, anonymousGroupId: String
    ): AGMemberEntity {
        return AGMemberEntity(
            agMember.id,
            name = agMember.name,
            createdAt = agMember.createdAt.toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
            lastLatitude = agMember.lastLocationRecord?.coordinates?.latitude,
            lastLongitude = agMember.lastLocationRecord?.coordinates?.longitude,
            lastSeen = agMember.lastLocationRecord?.timestamp?.toInstant(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds(),
            anonymousGroupId = anonymousGroupId,
        )
    }

    suspend fun toDomain(
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