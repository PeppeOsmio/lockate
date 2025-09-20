package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import kotlinx.serialization.Serializable

@Serializable
data class AGLocationSaveRequestDto(val encryptedLocation: EncryptedStringDto)
