package com.peppeosmio.lockate.platform_service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreService(
) {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getKey(KEY_ALIAS, null) as SecretKey)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    suspend fun encrypt(data: ByteArray): EncryptedData = withContext(Dispatchers.Default) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val cipherText = cipher.doFinal(data)

        EncryptedData(cipherText = cipherText, iv = iv)
    }

    suspend fun decrypt(encryptedData: EncryptedData): ByteArray =
        withContext(Dispatchers.Default) {
            val secretKey = getOrCreateKey()
            val spec = GCMParameterSpec(128, encryptedData.iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(encryptedData.cipherText)
        }

    companion object {
        private const val KEY_ALIAS = "LockateMasterKey"
    }
}