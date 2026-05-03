package com.peppeosmio.lockate.data.anonymous_group.remote

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
data class EncryptedDataDto(
    val cipherText: String,
    val iv: String,
)
