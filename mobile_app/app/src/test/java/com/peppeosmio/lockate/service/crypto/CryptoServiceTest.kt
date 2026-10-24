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
        val key = cryptoService.generateGroupKey()
        val plaintext = "hello world".toByteArray()

        val encrypted = cryptoService.encrypt(plaintext, key)
        val decrypted = cryptoService.decrypt(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt then decrypt with empty plaintext returns empty plaintext`() = runTest {
        val key = cryptoService.generateGroupKey()
        val plaintext = ByteArray(0)

        val encrypted = cryptoService.encrypt(plaintext, key)
        val decrypted = cryptoService.decrypt(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with the wrong key throws CryptoException`() = runTest {
        val key = cryptoService.generateGroupKey()
        val wrongKey = cryptoService.generateGroupKey()
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
        val key = cryptoService.generateGroupKey()
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
        val key = cryptoService.generateGroupKey()
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
            val key = cryptoService.generateGroupKey()
            val plaintext = "hello world".toByteArray()

            val first = cryptoService.encrypt(plaintext, key)
            val second = cryptoService.encrypt(plaintext, key)

            assertFalse(first.iv.contentEquals(second.iv))
            assertFalse(first.cipherText.contentEquals(second.cipherText))
        }

    @Test
    fun `generateGroupKey returns a new 32-byte key every time`() {
        val key1 = cryptoService.generateGroupKey()
        val key2 = cryptoService.generateGroupKey()

        assertEquals(32, key1.size)
        assertNotEquals(key1.toList(), key2.toList())
    }
}
