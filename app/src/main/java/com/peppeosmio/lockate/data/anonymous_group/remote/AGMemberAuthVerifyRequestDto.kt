package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGMemberAuthVerifyRequestDto (
    val encryptedMemberName: EncryptedDataDto,
    val srpSessionId: String,
    val M1: String
)