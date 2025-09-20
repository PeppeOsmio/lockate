package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.service.location.Location
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
    val lastLocation: AGLocation?
) {
    @OptIn(ExperimentalTime::class)
    fun toEntity(anonymousGroupId: String): AGMemberEntity {
        return AGMemberEntity(
            id = id,
            name = name,
            createdAt = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            lastSeen = lastLocation?.timestamp?.toInstant(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds(),
            lastLatitude = lastLocation?.coordinates?.latitude,
            lastLongitude = lastLocation?.coordinates?.longitude,
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
                lastLocation = entity.lastLatitude?.let {
                    AGLocation(
                        coordinates = Location(
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
