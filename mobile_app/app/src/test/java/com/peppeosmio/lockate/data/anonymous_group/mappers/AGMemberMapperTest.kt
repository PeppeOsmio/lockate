package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedDataDto
import com.peppeosmio.lockate.service.crypto.CryptoService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.ExperimentalTime

class AGMemberMapperTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun `entityToDomain then domainToEntity round trip preserves scalar fields`() {
        val createdAt = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val entity = AGMemberEntity(
            internalId = 1L,
            id = "member-1",
            name = "Alice",
            createdAt = createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            lastLatitude = 40.8517746,
            lastLongitude = 14.2681244,
            lastSeen = createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            isAGAdmin = true,
            anonymousGroupInternalId = 2L
        )

        val domain = AGMemberMapper.entityToDomain(entity)
        val roundTripped = AGMemberMapper.domainToEntity(domain, anonymousGroupInternalId = 2L)

        assertEquals(entity, roundTripped)
    }

    @Test
    fun `entityToDomain maps a null lastLatitude to a null lastLocationRecord`() {
        val entity = AGMemberEntity(
            internalId = 1L,
            id = "member-1",
            name = "Alice",
            createdAt = 0L,
            lastLatitude = null,
            lastLongitude = null,
            lastSeen = null,
            isAGAdmin = false,
            anonymousGroupInternalId = 2L
        )

        val domain = AGMemberMapper.entityToDomain(entity)

        assertNull(domain.lastLocationRecord)
    }

    @Test
    fun `dtoToDomain decrypts the member name and internalId is always 0`() = runTest {
        val cryptoService = mockk<CryptoService>()
        val key = "key".toByteArray()
        coEvery { cryptoService.decrypt(any(), key) } returns "Alice".toByteArray()
        val createdAt = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val dto = EncryptedAGMemberDto(
            id = "member-1",
            encryptedName = EncryptedDataDto(cipherText = "", iv = ""),
            createdAt = createdAt,
            encryptedLastLocationRecord = null,
            isAGAdmin = true
        )

        val result = AGMemberMapper.dtoToDomain(dto, cryptoService, key)

        assertEquals(0L, result.internalId)
        assertEquals("Alice", result.name)
        assertEquals("member-1", result.id)
        assertEquals(createdAt, result.createdAt)
        assertNull(result.lastLocationRecord)
        assertEquals(true, result.isAGAdmin)
    }
}
