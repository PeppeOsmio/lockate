package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/** An event of the locations stream: a member's position. */
@Serializable
data class MemberLocationDto(
    val memberId: String,
    val record: EncryptedLocationRecordDto,
)
