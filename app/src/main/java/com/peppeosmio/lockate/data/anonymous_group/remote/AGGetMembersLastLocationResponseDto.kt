package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGGetMembersLastLocationResponseDto(
    val locations: Map<String, EncryptedAGLocationDto>
)
