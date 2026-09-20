package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.ExperimentalTime

class AnonymousGroupMapperTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun `toEntity then toDomain round trip preserves fields`() = runTest {
        val keyStoreService = mockk<KeyStoreService>()
        val memberToken = "token".toByteArray()
        val key = "key".toByteArray()
        val encryptedMemberToken =
            EncryptedData(cipherText = byteArrayOf(1, 2), iv = byteArrayOf(3, 4))
        val encryptedKey = EncryptedData(cipherText = byteArrayOf(5, 6), iv = byteArrayOf(7, 8))
        coEvery { keyStoreService.encrypt(memberToken) } returns encryptedMemberToken
        coEvery { keyStoreService.encrypt(key) } returns encryptedKey
        coEvery { keyStoreService.decrypt(encryptedMemberToken) } returns memberToken
        coEvery { keyStoreService.decrypt(encryptedKey) } returns key

        val createdAt = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val joinedAt = LocalDateTime(2026, 7, 12, 15, 0, 0)
        val anonymousGroup = AnonymousGroup(
            internalId = 1L,
            id = "ag-1",
            name = "Test Group",
            createdAt = createdAt,
            joinedAt = joinedAt,
            memberName = "Alice",
            memberId = "member-1",
            memberToken = memberToken,
            memberIsAGAdmin = true,
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = key,
            connectionId = 2L
        )

        val entity = AnonymousGroupMapper.toEntity(anonymousGroup, keyStoreService)
        val roundTripped = AnonymousGroupMapper.toDomain(entity, keyStoreService)

        assertEquals(anonymousGroup, roundTripped)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `toEntity converts createdAt and joinedAt to UTC epoch millis`() = runTest {
        val keyStoreService = mockk<KeyStoreService>()
        coEvery { keyStoreService.encrypt(any()) } returns EncryptedData(
            cipherText = ByteArray(0), iv = ByteArray(0)
        )
        val createdAt = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val joinedAt = LocalDateTime(2026, 7, 12, 15, 0, 0)
        val anonymousGroup = AnonymousGroup(
            internalId = 1L,
            id = "ag-1",
            name = "Test Group",
            createdAt = createdAt,
            joinedAt = joinedAt,
            memberName = "Alice",
            memberId = "member-1",
            memberToken = ByteArray(0),
            memberIsAGAdmin = false,
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = ByteArray(0),
            connectionId = 2L
        )

        val entity = AnonymousGroupMapper.toEntity(anonymousGroup, keyStoreService)

        assertEquals(createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds(), entity.createdAt)
        assertEquals(joinedAt.toInstant(TimeZone.UTC).toEpochMilliseconds(), entity.joinedAt)
    }
}
