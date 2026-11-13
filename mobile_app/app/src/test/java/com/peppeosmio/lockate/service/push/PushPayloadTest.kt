package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PushPayloadTest {
    private val crypto = CryptoService()
    private val key = ByteArray(32) { 7 }
    private val r = Base64.encode(ByteArray(16) { 5 })

    // what the backend's AGPushSender does
    private suspend fun seal(plaintext: ByteArray, registrationId: String = r) =
        crypto.encrypt(plaintext, key, (PUSH_AAD_PREFIX + registrationId).encodeToByteArray())

    @Test
    fun `a payload sealed for the registration opens`() = runTest {
        val sealed = seal("frame".encodeToByteArray())

        val opened =
            openPushPayload(
                crypto,
                key,
                r,
                Base64.encode(sealed.iv),
                Base64.encode(sealed.cipherText),
            )

        assertArrayEquals("frame".encodeToByteArray(), opened)
    }

    @Test
    fun `a payload sealed for another registration doesn't open`() = runTest {
        val sealed = seal("frame".encodeToByteArray(), Base64.encode(ByteArray(16)))

        assertThrows(CryptoException::class.java) {
            kotlinx.coroutines.runBlocking {
                openPushPayload(
                    crypto,
                    key,
                    r,
                    Base64.encode(sealed.iv),
                    Base64.encode(sealed.cipherText),
                )
            }
        }
    }

    @Test
    fun `a payload sealed with another key doesn't open`() = runTest {
        val sealed = seal("frame".encodeToByteArray())

        assertThrows(CryptoException::class.java) {
            kotlinx.coroutines.runBlocking {
                openPushPayload(
                    crypto,
                    ByteArray(32),
                    r,
                    Base64.encode(sealed.iv),
                    Base64.encode(sealed.cipherText),
                )
            }
        }
    }
}
