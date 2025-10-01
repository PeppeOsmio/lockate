package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGCreateRequestDto(
    val encryptedMemberName: EncryptedDataDto,
    val encryptedGroupName: EncryptedDataDto,
    val memberPasswordSrpVerifier: String,
    val memberPasswordSrpSalt: String,
    val adminPassword: String,
    val keySalt: String
)