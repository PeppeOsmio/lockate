package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlin.io.encoding.Base64

const val PUSH_AAD_PREFIX = "lockate-push-v1|"

/**
 * Opens what the server sealed in a push, `{r, iv, c}`: AES-GCM with the registration's [key] and
 * the AAD `lockate-push-v1|` + [r] as sent, so a payload moved to another registration doesn't
 * open. Throws `CryptoException` when it doesn't, `IllegalArgumentException` for bad Base64.
 */
suspend fun openPushPayload(
    cryptoService: CryptoService,
    key: ByteArray,
    r: String,
    iv: String,
    c: String,
): ByteArray =
    cryptoService.decrypt(
        EncryptedData(Base64.decode(c), Base64.decode(iv)),
        key,
        (PUSH_AAD_PREFIX + r).encodeToByteArray(),
    )
