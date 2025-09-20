package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlinx.serialization.Serializable

@Serializable
data class AGCreateRequestDto(
    val encryptedMemberName: EncryptedStringDto,
    val encryptedGroupName: EncryptedStringDto,
    val memberPasswordSrpVerifier: String,
    val memberPasswordSrpSalt: String,
    val adminPassword: String,
)