package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.serialization.Serializable

@Serializable
data class AGLocationUpdateDto(
    val location: EncryptedLocationRecordDto, val agMemberId: String
)
