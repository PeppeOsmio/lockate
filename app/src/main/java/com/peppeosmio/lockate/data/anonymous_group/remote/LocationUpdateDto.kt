package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class LocationUpdateDto(
    val location: EncryptedLocationRecordDto, val memberId: String
)
