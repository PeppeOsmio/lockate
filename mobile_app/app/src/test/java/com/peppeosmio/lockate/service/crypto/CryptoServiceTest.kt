package com.peppeosmio.lockate.service.crypto

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class CryptoServiceTest {

    private val cryptoService = CryptoService()

    @Test
    fun `encrypt then decrypt with the same key returns the original plaintext`() = runTest {
        val key = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
        val plaintext = "hello world".toByteArray()

        val encrypted = cryptoService.encrypt(plaintext, key)
        val decrypted = cryptoService.decrypt(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt then decrypt with empty plaintext returns empty plaintext`() = runTest {
        val key = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
        val plaintext = ByteArray(0)

        val encrypted = cryptoService.encrypt(plaintext, key)
        val decrypted = cryptoService.decrypt(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with the wrong key throws CryptoException`() = runTest {
        val salt = cryptoService.getKeySalt()
        val key = cryptoService.createKey("password".toByteArray(), salt)
        val wrongKey = cryptoService.createKey("different-password".toByteArray(), salt)
        val encrypted = cryptoService.encrypt("hello world".toByteArray(), key)

        try {
            cryptoService.decrypt(encrypted, wrongKey)
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `decrypt with tampered cipherText throws CryptoException`() = runTest {
        val key = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
        val encrypted = cryptoService.encrypt("hello world".toByteArray(), key)
        val tamperedCipherText = encrypted.cipherText.copyOf()
        tamperedCipherText[0] = (tamperedCipherText[0] + 1).toByte()
        val tampered = EncryptedData(cipherText = tamperedCipherText, iv = encrypted.iv)

        try {
            cryptoService.decrypt(tampered, key)
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `decrypt with tampered iv throws CryptoException`() = runTest {
        val key = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
        val encrypted = cryptoService.encrypt("hello world".toByteArray(), key)
        val tamperedIv = encrypted.iv.copyOf()
        tamperedIv[0] = (tamperedIv[0] + 1).toByte()
        val tampered = EncryptedData(cipherText = encrypted.cipherText, iv = tamperedIv)

        try {
            cryptoService.decrypt(tampered, key)
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `two encrypt calls on the same plaintext and key produce different iv and cipherText`() =
        runTest {
            val key = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
            val plaintext = "hello world".toByteArray()

            val first = cryptoService.encrypt(plaintext, key)
            val second = cryptoService.encrypt(plaintext, key)

            assertFalse(first.iv.contentEquals(second.iv))
            assertFalse(first.cipherText.contentEquals(second.cipherText))
        }

    @Test
    fun `createKey is deterministic for the same password and salt`() = runTest {
        val salt = cryptoService.getKeySalt()

        val key1 = cryptoService.createKey("password".toByteArray(), salt)
        val key2 = cryptoService.createKey("password".toByteArray(), salt)

        assertArrayEquals(key1, key2)
    }

    @Test
    fun `createKey differs across salts for the same password`() = runTest {
        val key1 = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())
        val key2 = cryptoService.createKey("password".toByteArray(), cryptoService.getKeySalt())

        assertNotEquals(key1.toList(), key2.toList())
    }

    @Test
    fun `getKeySalt returns 16 bytes`() {
        assertEquals(16, cryptoService.getKeySalt().size)
    }
}
