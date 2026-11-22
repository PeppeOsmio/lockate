package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionMapperTest {
    // a stand-in for the Keystore that reverses the bytes, so the stored key isn't the plain one
    private val keyStoreService =
        mockk<KeyStoreService> {
            coEvery { encrypt(any()) } answers
                {
                    EncryptedData(
                        cipherText = firstArg<ByteArray>().reversedArray(),
                        iv = ByteArray(12),
                    )
                }
            coEvery { decrypt(any()) } answers
                {
                    firstArg<EncryptedData>().cipherText.reversedArray()
                }
        }

    @Test
    fun `the API key is stored encrypted and read back`() = runTest {
        val connection =
            Connection(id = 42L, name = "Home", url = "https://example.com", apiKey = "secret-key")

        val entity = ConnectionMapper.toEntity(connection, keyStoreService)

        assertFalse(entity.apiKeyCipher!!.contentEquals("secret-key".toByteArray()))
        assertEquals(connection, ConnectionMapper.toDomain(entity, keyStoreService))
    }

    @Test
    fun `a server without API key stores none`() = runTest {
        val connection =
            Connection(id = null, name = "New", url = "https://example.com", apiKey = null)

        val entity = ConnectionMapper.toEntity(connection, keyStoreService)

        assertEquals(0L, entity.id)
        assertNull(entity.apiKeyCipher)
        assertNull(ConnectionMapper.toDomain(entity, keyStoreService).apiKey)
    }
}
