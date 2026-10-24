package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGJoinReqDto(val joinToken: String, val encryptedMemberName: EncryptedDataDto)
