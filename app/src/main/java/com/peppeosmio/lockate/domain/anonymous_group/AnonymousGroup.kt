package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class AnonymousGroup(
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
    val joinedAt: LocalDateTime,
    val memberName: String,
    val memberId: String,
    val memberToken: String,
    val adminToken: String?,
    val isMember: Boolean,
    val existsRemote: Boolean,
    val memberPassword: String,
    val sendLocation: Boolean
) {
    @OptIn(ExperimentalTime::class)
    fun toEntity(connectionSettingsId: Long): AnonymousGroupEntity {
        return AnonymousGroupEntity(
            id = id,
            name = name,
            createdAt = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            joinedAt = joinedAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            memberName = memberName,
            memberId = memberId,
            memberToken = Base64.decode(memberToken),
            adminToken = adminToken?.let { Base64.decode(adminToken) },
            isMember = isMember,
            existsRemote = existsRemote,
            memberPassword = memberPassword,
            sendLocation = sendLocation,
            connectionSettingsId = connectionSettingsId
        )
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        fun fromEntity(entity: AnonymousGroupEntity): AnonymousGroup {
            return AnonymousGroup(
                id = entity.id,
                name = entity.name,
                createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
                joinedAt = Instant.fromEpochMilliseconds(entity.joinedAt)
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
                memberName = entity.memberName,
                memberId = entity.memberId,
                memberToken = Base64.encode(entity.memberToken),
                memberPassword = entity.memberPassword,
                adminToken = entity.adminToken?.let { Base64.encode(entity.adminToken) },
                isMember = entity.isMember,
                existsRemote = entity.existsRemote,
                sendLocation = entity.sendLocation
            )
        }
    }
}