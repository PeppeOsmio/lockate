package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/**
 * A position with the place events it caused, posted to `…/locations`. [placesVersion] is the
 * version of the places the events were computed on; null before we know one.
 */
@Serializable
data class AGPostLocationReqDto(
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedActivity: EncryptedDataDto,
    val placeEvents: List<EncryptedDataDto>,
    val placesVersion: Long?,
)

/**
 * The version of the group's places, which goes up whenever one is created, edited or deleted: the
 * answer of a location post, also when it is refused (409) for events on other places.
 */
@Serializable data class AGPostLocationResDto(val placesVersion: Long)
