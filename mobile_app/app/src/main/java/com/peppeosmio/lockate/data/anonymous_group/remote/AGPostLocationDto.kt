package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/**
 * A position with the circle events it caused, posted to `…/locations`. [circlesVersion] is the
 * version of the circles the events were computed on; null before we know one.
 */
@Serializable
data class AGPostLocationReqDto(
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedActivity: EncryptedDataDto,
    val circleEvents: List<EncryptedDataDto>,
    val circlesVersion: Long?,
)

/**
 * The version of the group's circles, which goes up whenever one is created, edited or deleted: the
 * answer of a location post, also when it is refused (409) for events on other circles.
 */
@Serializable data class AGPostLocationResDto(val circlesVersion: Long)
