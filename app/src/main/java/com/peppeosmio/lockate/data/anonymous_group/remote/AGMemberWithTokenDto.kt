package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGMemberWithTokenDto(val member: EncryptedAGMemberDto, val token: String)
