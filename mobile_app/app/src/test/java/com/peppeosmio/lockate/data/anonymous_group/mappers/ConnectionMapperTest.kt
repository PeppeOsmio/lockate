package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.domain.Connection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionMapperTest {

    @Test
    fun `toEntity then toDomain round trip preserves all fields`() {
        val connection = Connection(
            id = 42L,
            name = "Home Server",
            url = "https://example.com",
            apiKey = "secret-key",
            username = null,
            authToken = null
        )

        val roundTripped = ConnectionMapper.toDomain(ConnectionMapper.toEntity(connection))

        assertEquals(connection, roundTripped)
    }

    @Test
    fun `toEntity maps a null id to 0`() {
        val connection = Connection(
            id = null, name = "New", url = "https://example.com", apiKey = null,
            username = null, authToken = null
        )

        val entity = ConnectionMapper.toEntity(connection)

        assertEquals(0L, entity.id)
    }

    @Test
    fun `toEntity and toDomain preserve a null apiKey`() {
        val connection = Connection(
            id = 1L, name = "Home", url = "https://example.com", apiKey = null,
            username = null, authToken = null
        )

        val entity = ConnectionMapper.toEntity(connection)
        val roundTripped = ConnectionMapper.toDomain(entity)

        assertNull(entity.apiKey)
        assertNull(roundTripped.apiKey)
    }
}
