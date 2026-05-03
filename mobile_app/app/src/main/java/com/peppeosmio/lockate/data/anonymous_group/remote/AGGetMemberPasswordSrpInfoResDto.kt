package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGGetMemberPasswordSrpInfoResDto(
    val encryptedName: EncryptedDataDto,
    val srpSalt: String,
    val keySalt: String
)
