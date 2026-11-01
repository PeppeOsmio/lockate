package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.service.crypto.CryptoException

/**
 * Any member can upload a blob that the others can't decrypt, and that must not take the rest of
 * the group down with it.
 */
internal suspend fun <T, R> List<T>.mapDecryptable(
    onUndecryptable: (T) -> Unit, transform: suspend (T) -> R
): List<R> = mapNotNull { item ->
    try {
        transform(item)
    } catch (e: CryptoException) {
        onUndecryptable(item)
        null
    }
}
