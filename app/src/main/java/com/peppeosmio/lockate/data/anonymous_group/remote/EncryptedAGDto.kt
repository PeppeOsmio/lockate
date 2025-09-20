package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedAGDto(
    val id: String,
    val encryptedName: EncryptedStringDto,
    val createdAt: LocalDateTime,
) {
}