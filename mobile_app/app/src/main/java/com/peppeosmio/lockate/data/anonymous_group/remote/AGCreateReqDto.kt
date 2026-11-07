package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

@Serializable
data class AGCreateReqDto(
    val groupId: String,
    val memberId: String,
    val encryptedMemberName: EncryptedDataDto,
    val encryptedGroupName: EncryptedDataDto,
)
