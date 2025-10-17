package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGMemberAuthVerifyResDto(
    val anonymousGroup: EncryptedAGDto,
    val authenticatedMemberInfo: AGMemberWithTokenDto,
    val members: List<EncryptedAGMemberDto>
)
