package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/** A position with the circle events it caused, posted to `…/locations`. */
@Serializable
data class AGPostLocationReqDto(
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedActivity: EncryptedDataDto,
    val circleEvents: List<EncryptedDataDto>,
)

/** The version of the group's circles, which goes up whenever one is created, edited or deleted. */
@Serializable data class AGPostLocationResDto(val circlesVersion: Long)
