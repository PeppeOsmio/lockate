package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A message sent on the group's websocket, encoded as `AGMemberMessageReqDto` to get its `type`.
 */
@Serializable
sealed interface AGMemberMessageReqDto {
    @Serializable
    @SerialName("member.location")
    data class Location(
        val encryptedCoordinates: EncryptedDataDto,
        val encryptedActivity: EncryptedDataDto,
    ) : AGMemberMessageReqDto

    @Serializable
    @SerialName("circle.event")
    data class CircleEvent(val encryptedEvent: EncryptedDataDto) : AGMemberMessageReqDto

    @Serializable @SerialName("member.paused") data object Paused : AGMemberMessageReqDto

    @Serializable @SerialName("ping") data object Ping : AGMemberMessageReqDto
}
