package com.peppeosmio.lockate.data.anonymous_group.mappers

import android.util.Log
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object AnonymousGroupMapper {
    @OptIn(ExperimentalTime::class)
    suspend fun toEntity(
        anonymousGroup: AnonymousGroup, connectionSettingsId: Long, keyStoreService: KeyStoreService
    ): AnonymousGroupEntity {
        val encryptedMemberToken = keyStoreService.encrypt(anonymousGroup.memberToken)
        val encryptedAdminToken = anonymousGroup.adminToken?.let {
            keyStoreService.encrypt(it)
        }
        val encryptedKey = keyStoreService.encrypt(anonymousGroup.key)
        return AnonymousGroupEntity(
            id = anonymousGroup.id,
            name = anonymousGroup.name,
            createdAt = anonymousGroup.createdAt.toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
            joinedAt = anonymousGroup.joinedAt.toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),

            isMember = anonymousGroup.isMember,
            existsRemote = anonymousGroup.existsRemote,
            sendLocation = anonymousGroup.sendLocation,
            connectionSettingsId = connectionSettingsId,

            memberName = anonymousGroup.memberName,
            memberId = anonymousGroup.memberId,
            memberTokenCipher = encryptedMemberToken.cipherText,
            memberTokenIv = encryptedMemberToken.iv,

            adminTokenCipher = encryptedAdminToken?.cipherText,
            adminTokenIv = encryptedAdminToken?.iv,

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
        val adminToken = entity.adminTokenCipher?.let {
            keyStoreService.decrypt(
                EncryptedData(
                    cipherText = entity.adminTokenCipher, iv = entity.adminTokenIv!!
                )
            )
        }
        return AnonymousGroup(
            id = entity.id,
            name = entity.name,
            createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            joinedAt = Instant.fromEpochMilliseconds(entity.joinedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            memberName = entity.memberName,
            memberId = entity.memberId,
            memberToken = memberToken,
            adminToken = adminToken,
            isMember = entity.isMember,
            existsRemote = entity.existsRemote,
            sendLocation = entity.sendLocation,
            key = key
        )
    }

}