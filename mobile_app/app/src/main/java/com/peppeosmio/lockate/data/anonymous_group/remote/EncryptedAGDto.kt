package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedAGDto(
    val id: String,
    val encryptedName: EncryptedDataDto,
    val createdAt: LocalDateTime,
    val keySalt: String
) {
}