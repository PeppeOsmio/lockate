package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A message sent on the group's websocket, encoded as `AGMemberMessageReqDto` to get its `type`.
 * Everything else we send goes over REST.
 */
@Serializable
sealed interface AGMemberMessageReqDto {
    @Serializable @SerialName("ping") data object Ping : AGMemberMessageReqDto
}

/** A position with the circle events it caused, posted to `…/locations`. */
@Serializable
data class AGPostLocationReqDto(
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedActivity: EncryptedDataDto,
    val circleEvents: List<EncryptedDataDto>,
)
