package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object AnonymousGroupMapper {
    @OptIn(ExperimentalTime::class)
    suspend fun toEntity(
        anonymousGroup: AnonymousGroup, keyStoreService: KeyStoreService
    ): AnonymousGroupEntity {
        val encryptedMemberToken = keyStoreService.encrypt(anonymousGroup.memberToken)
        val encryptedKey = keyStoreService.encrypt(anonymousGroup.key)
        return AnonymousGroupEntity(
            internalId = anonymousGroup.internalId,
            id = anonymousGroup.id,
            name = anonymousGroup.name,
            createdAt = anonymousGroup.createdAt.toInstant(TimeZone.UTC)
                .toEpochMilliseconds(),
            joinedAt = anonymousGroup.joinedAt.toInstant(TimeZone.UTC)
                .toEpochMilliseconds(),

            isMember = anonymousGroup.isMember,
            existsRemote = anonymousGroup.existsRemote,
            sendLocation = anonymousGroup.sendLocation,
            connectionId = anonymousGroup.connectionId,

            memberName = anonymousGroup.memberName,
            memberId = anonymousGroup.memberId,
            memberTokenCipher = encryptedMemberToken.cipherText,
            memberTokenIv = encryptedMemberToken.iv,
            memberIsAGAdmin = anonymousGroup.memberIsAGAdmin,

            keyCipher = encryptedKey.cipherText,
            keyIv = encryptedKey.iv,
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun toDomain(
        entity: AnonymousGroupEntity,
        keyStoreService: KeyStoreService,
    ): AnonymousGroup {
        val key = keyStoreService.decrypt(
            EncryptedData(
                cipherText = entity.keyCipher, iv = entity.keyIv
            )
        )
        val memberToken = keyStoreService.decrypt(
            EncryptedData(
                cipherText = entity.memberTokenCipher, iv = entity.memberTokenIv
            )
        )
        return AnonymousGroup(
            internalId = entity.internalId,
            id = entity.id,
            name = entity.name,
            createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                .toLocalDateTime(TimeZone.UTC),
            joinedAt = Instant.fromEpochMilliseconds(entity.joinedAt)
                .toLocalDateTime(TimeZone.UTC),
            memberName = entity.memberName,
            memberId = entity.memberId,
            memberToken = memberToken,
            memberIsAGAdmin = entity.memberIsAGAdmin,
            isMember = entity.isMember,
            existsRemote = entity.existsRemote,
            sendLocation = entity.sendLocation,
            key = key,
            connectionId = entity.connectionId
        )
    }

}