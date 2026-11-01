package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CircleDto(
    val id: String,
    val encryptedData: EncryptedDataDto,
    val updatedAt: LocalDateTime,
)

@Serializable
data class CircleUpsertReqDto(val encryptedData: EncryptedDataDto)
