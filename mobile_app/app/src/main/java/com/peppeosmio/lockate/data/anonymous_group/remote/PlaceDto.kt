package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val id: String,
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedRadius: EncryptedDataDto,
    val encryptedName: EncryptedDataDto,
    val updatedAt: LocalDateTime,
)

@Serializable
data class PlaceUpsertReqDto(
    val encryptedCoordinates: EncryptedDataDto,
    val encryptedRadius: EncryptedDataDto,
    val encryptedName: EncryptedDataDto,
)
