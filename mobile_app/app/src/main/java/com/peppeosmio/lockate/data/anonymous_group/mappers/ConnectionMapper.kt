package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity
import com.peppeosmio.lockate.domain.Connection

object ConnectionMapper {
    fun toEntity(connection: Connection): ConnectionEntity {
        return ConnectionEntity(
            id = connection.id ?: 0,
            name = connection.name,
            url = connection.url,
            apiKey = connection.apiKey,
            username = connection.username,
            authToken = connection.authToken
        )
    }

    fun toDomain(entity : ConnectionEntity): Connection {
        return Connection(
            id = entity.id,
            name = entity.name,
            url = entity.url,
            apiKey = entity.apiKey,
            username = entity.username,
            authToken = entity.authToken
        )
    }
}