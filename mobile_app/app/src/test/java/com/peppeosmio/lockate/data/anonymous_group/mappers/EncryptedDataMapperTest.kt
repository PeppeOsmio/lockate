package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.domain.crypto.EncryptedData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.encoding.Base64

class EncryptedDataMapperTest {

    @Test
    fun `toDto then toDomain round trip preserves the original bytes`() {
        val encryptedData = EncryptedData(
            cipherText = byteArrayOf(1, 2, 3, 4, 5), iv = byteArrayOf(6, 7, 8)
        )

        val roundTripped = EncryptedDataMapper.toDomain(EncryptedDataMapper.toDto(encryptedData))

        assertArrayEquals(encryptedData.cipherText, roundTripped.cipherText)
        assertArrayEquals(encryptedData.iv, roundTripped.iv)
    }

    @Test
    fun `toDto encodes known bytes to the expected Base64 string`() {
        val encryptedData = EncryptedData(
            cipherText = "hello".toByteArray(), iv = "iv".toByteArray()
        )

        val dto = EncryptedDataMapper.toDto(encryptedData)

        assertEquals(Base64.encode("hello".toByteArray()), dto.cipherText)
        assertEquals(Base64.encode("iv".toByteArray()), dto.iv)
    }

    @Test
    fun `empty byte arrays round trip`() {
        val encryptedData = EncryptedData(cipherText = ByteArray(0), iv = ByteArray(0))

        val roundTripped = EncryptedDataMapper.toDomain(EncryptedDataMapper.toDto(encryptedData))

        assertArrayEquals(ByteArray(0), roundTripped.cipherText)
        assertArrayEquals(ByteArray(0), roundTripped.iv)
    }
}
