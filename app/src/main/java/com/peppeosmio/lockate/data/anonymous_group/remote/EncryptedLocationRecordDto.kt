package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedLocationRecordDto(
    val encryptedCoordinates: EncryptedDataDto,
    val timestamp: LocalDateTime,
)

