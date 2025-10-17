package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGMemberAuthStartResDto(
    val srpSessionId: String,
    val B: String,
)
