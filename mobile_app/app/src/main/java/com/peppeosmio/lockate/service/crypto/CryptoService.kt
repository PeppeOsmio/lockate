package com.peppeosmio.lockate.service.crypto

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CryptoService(
) {

    companion object {
        const val GROUP_KEY_BYTES = 32
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
        const val AES_GCM_TAG_BYTES = AES_GCM_TAG_BITS / 8
    }

    private fun getRandomBytes(length: Int = 32): ByteArray {
        val bytes = ByteArray(length)
        CryptographyRandom.nextBytes(bytes)
        return bytes
    }

    /** The group's AES-256 key: random, and shared with new members only through invites */
    fun generateGroupKey(): ByteArray {
        return getRandomBytes(GROUP_KEY_BYTES)
    }

    private fun getIv(): ByteArray {
        return getRandomBytes(AES_GCM_IV_BYTES)
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun encrypt(
        data: ByteArray, key: ByteArray
    ): EncryptedData = withContext(Dispatchers.Default) {

        val provider = CryptographyProvider.Default
        val ivBytes = getIv()
        val gcmBlockCipher = provider.get(AES.GCM)
        val parsedKey = gcmBlockCipher.keyDecoder()
            .decodeFromByteArray(format = AES.Key.Format.RAW, bytes = key)
        try {
            val cipherText = parsedKey.cipher(AES_GCM_TAG_BITS.bits)
                .encryptWithIv(iv = ivBytes, plaintext = data)
            EncryptedData(
                cipherText = cipherText, iv = ivBytes
            )
        } catch (e: Exception) {
            throw CryptoException()
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun decrypt(
        encryptedData: EncryptedData, key: ByteArray
    ): ByteArray = withContext(Dispatchers.Default) {
        val provider = CryptographyProvider.Default
        val gcmBlockCipher = provider.get(AES.GCM)
        val parsedKey = gcmBlockCipher.keyDecoder()
            .decodeFromByteArray(format = AES.Key.Format.RAW, bytes = key)
        try {
            val output = parsedKey.cipher(AES_GCM_TAG_BITS.bits)
                .decryptWithIv(iv = encryptedData.iv, ciphertext = encryptedData.cipherText)
            output
        } catch (e: Exception) {
            throw CryptoException()
        }
    }
}
