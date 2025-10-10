package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionSettingsEntity
import com.peppeosmio.lockate.domain.ConnectionSettings

object ConnectionSettingsMapper {
    fun toEntity(connectionSettings: ConnectionSettings): ConnectionSettingsEntity {
        return ConnectionSettingsEntity(
            id = connectionSettings.id ?: 0,
            url = connectionSettings.url,
            apiKey = connectionSettings.apiKey,
            username = connectionSettings.username,
            authToken = connectionSettings.authToken
        )
    }

    fun toDomain(entity : ConnectionSettingsEntity): ConnectionSettings {
        return ConnectionSettings(
            id = entity.id,
            url = entity.url,
            apiKey = entity.apiKey,
            username = entity.username,
            authToken = entity.authToken
        )
    }
}