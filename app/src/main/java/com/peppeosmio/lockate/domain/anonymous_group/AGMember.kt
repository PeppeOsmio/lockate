package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.domain.Coordinates
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class AGMember(
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
    val lastLocationRecord: LocationRecord?
) {
    @OptIn(ExperimentalTime::class)
    fun toEntity(anonymousGroupId: String): AGMemberEntity {
        return AGMemberEntity(
            id = id,
            name = name,
            createdAt = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            lastSeen = lastLocationRecord?.timestamp?.toInstant(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds(),
            lastLatitude = lastLocationRecord?.coordinates?.latitude,
            lastLongitude = lastLocationRecord?.coordinates?.longitude,
            anonymousGroupId = anonymousGroupId,
        )
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        fun fromEntity(entity: AGMemberEntity): AGMember {
            return AGMember(
                id = entity.id,
                name = entity.name,
                createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
                lastLocationRecord = entity.lastLatitude?.let {
                    LocationRecord(
                        coordinates = Coordinates(
                            latitude = entity.lastLatitude,
                            longitude = entity.lastLongitude!!

                        ),
                        timestamp = Instant.fromEpochMilliseconds(entity.lastSeen!!)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                    )
                })
        }
    }
}
