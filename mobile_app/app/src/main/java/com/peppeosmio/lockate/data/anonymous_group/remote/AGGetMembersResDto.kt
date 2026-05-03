package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable


@Serializable
data class AGGetMembersResDto(val members: List<EncryptedAGMemberDto>)
