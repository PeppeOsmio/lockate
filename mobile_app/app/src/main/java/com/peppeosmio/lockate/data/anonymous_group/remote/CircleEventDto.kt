package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CircleEventDto(
    val encryptedEvent: EncryptedDataDto,
    val timestamp: LocalDateTime,
)
