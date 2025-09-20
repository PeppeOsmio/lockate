package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlinx.serialization.Serializable

@Serializable
data class AGMemberAuthVerifyRequestDto (
    val encryptedUserName: EncryptedStringDto,
    val srpSessionId: String,
    val M1: String
)