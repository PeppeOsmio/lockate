package com.peppeosmio.lockate.service.crypto

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CryptoService(
) {

    companion object {
        const val PBKDF2_KEY_BITS = 256
        const val PBKDF2_ITERATIONS = 600_000
        const val PBKDF2_SALT_BYTES = 16
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
        const val AES_GCM_TAG_BYTES = AES_GCM_TAG_BITS / 8
    }

    private fun getRandomBytes(length: Int = 32): ByteArray {
        val bytes = ByteArray(length)
        CryptographyRandom.nextBytes(bytes)
        return bytes
    }

    fun getKeySalt(): ByteArray {
        return getRandomBytes(PBKDF2_SALT_BYTES)
    }

    private fun getIv(): ByteArray {
        return getRandomBytes(AES_GCM_IV_BYTES)
    }

    suspend fun createKey(passwordBytes: ByteArray, keySalt: ByteArray): ByteArray {
        val provider = CryptographyProvider.Default
        val pbkdf2 = provider.get(PBKDF2)
        val sha256 = provider.get(SHA256)
        val secretDerivation = pbkdf2.secretDerivation(
            digest = sha256.id,
            iterations = PBKDF2_ITERATIONS,
            outputSize = PBKDF2_KEY_BITS.bits,
            salt = keySalt
        )
        return secretDerivation.deriveSecret(passwordBytes).toByteArray()
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
