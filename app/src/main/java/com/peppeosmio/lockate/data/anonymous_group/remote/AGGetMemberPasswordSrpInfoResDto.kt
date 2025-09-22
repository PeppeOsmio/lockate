package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlinx.serialization.Serializable

@Serializable
data class AGGetMemberPasswordSrpInfoResDto(
    val encryptedName: EncryptedStringDto,
    val salt: String
)
