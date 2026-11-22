package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService

object ConnectionMapper {
    suspend fun toEntity(
        connection: Connection,
        keyStoreService: KeyStoreService,
    ): ConnectionEntity {
        val apiKey = connection.apiKey?.let { keyStoreService.encrypt(it.toByteArray()) }
        return ConnectionEntity(
            id = connection.id ?: 0,
            name = connection.name,
            url = connection.url,
            apiKeyCipher = apiKey?.cipherText,
            apiKeyIv = apiKey?.iv,
        )
    }

    suspend fun toDomain(entity: ConnectionEntity, keyStoreService: KeyStoreService): Connection {
        val apiKey =
            if (entity.apiKeyCipher == null || entity.apiKeyIv == null) null
            else
                keyStoreService
                    .decrypt(EncryptedData(cipherText = entity.apiKeyCipher, iv = entity.apiKeyIv))
                    .decodeToString()
        return Connection(id = entity.id, name = entity.name, url = entity.url, apiKey = apiKey)
    }
}
