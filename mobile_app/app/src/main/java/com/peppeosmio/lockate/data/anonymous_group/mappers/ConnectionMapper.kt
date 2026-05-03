package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity
import com.peppeosmio.lockate.domain.Connection

object ConnectionMapper {
    fun toEntity(connection: Connection): ConnectionEntity {
        return ConnectionEntity(
            id = connection.id ?: 0,
            url = connection.url,
            apiKey = connection.apiKey,
            username = connection.username,
            authToken = connection.authToken
        )
    }

    fun toDomain(entity : ConnectionEntity): Connection {
        return Connection(
            id = entity.id,
            url = entity.url,
            apiKey = entity.apiKey,
            username = entity.username,
            authToken = entity.authToken
        )
    }
}