package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGCreateResponseDto(
    val anonymousGroup: EncryptedAGDto,
    val authenticatedMemberInfo: AGMemberWithTokenDto,
)
