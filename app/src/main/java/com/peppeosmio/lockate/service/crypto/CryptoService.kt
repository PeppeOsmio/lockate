package com.peppeosmio.lockate.service.crypto

import android.util.Log
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64

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

    fun getSalt(): ByteArray {
        return getRandomBytes(PBKDF2_SALT_BYTES)
    }

    private fun getIv(): ByteArray {
        return getRandomBytes(AES_GCM_IV_BYTES)
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun aesGcmEncrypt(
        plainTextBytes: ByteArray, passwordBytes: ByteArray, saltBytes: ByteArray
    ): EncryptedString = withContext(Dispatchers.Default) {

        val provider = CryptographyProvider.Default
        val pbkdf2 = provider.get(PBKDF2)
        val sha256 = provider.get(SHA256)
        val secretDerivation = pbkdf2.secretDerivation(
            digest = sha256.id,
            iterations = PBKDF2_ITERATIONS,
            outputSize = PBKDF2_KEY_BITS.bits,
            salt = saltBytes
        )
        val keyBytes = secretDerivation.deriveSecret(passwordBytes).toByteArray()
        val ivBytes = getIv()
        val gcmBlockCipher = provider.get(AES.GCM)
        val key = gcmBlockCipher.keyDecoder()
            .decodeFromByteArray(format = AES.Key.Format.RAW, bytes = keyBytes)
        try {
            val encryptedResult = key.cipher(AES_GCM_TAG_BITS.bits)
                .encryptWithIv(iv = ivBytes, plaintext = plainTextBytes)
            val cipherText =
                encryptedResult.copyOfRange(0, encryptedResult.size - AES_GCM_TAG_BYTES)
            val authTag = encryptedResult.copyOfRange(
                encryptedResult.size - AES_GCM_TAG_BYTES, encryptedResult.size
            )
            EncryptedString(
                cipherText = cipherText, salt = saltBytes, iv = ivBytes, authTag = authTag
            )
        } catch (e: Exception) {
            throw CryptoException()
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun aesGcmDecrypt(
        encryptedString: EncryptedString, passwordBytes: ByteArray
    ): ByteArray = withContext(Dispatchers.Default) {
        val provider = CryptographyProvider.Default
        val pbkdf2 = provider.get(PBKDF2)
        val sha256 = provider.get(SHA256)
        val secretDerivation = pbkdf2.secretDerivation(
            digest = sha256.id,
            iterations = PBKDF2_ITERATIONS,
            outputSize = PBKDF2_KEY_BITS.bits,
            salt = encryptedString.salt
        )
        val keyBytes = secretDerivation.deriveSecret(passwordBytes).toByteArray()
        val gcmBlockCipher = provider.get(AES.GCM)
        val key = gcmBlockCipher.keyDecoder()
            .decodeFromByteArray(format = AES.Key.Format.RAW, bytes = keyBytes)
        val fullCipherText = encryptedString.cipherText + encryptedString.authTag
        try {
            val output = key.cipher(AES_GCM_TAG_BITS.bits)
                .decryptWithIv(iv = encryptedString.iv, ciphertext = fullCipherText)
            output
        } catch (e: Exception) {
            throw CryptoException()
        }
    }
}
