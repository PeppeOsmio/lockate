package com.peppeosmio.lockate.domain

import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionSettingsEntity

data class ConnectionSettings(
    val id: Long?, val url: String, val apiKey: String?, val username: String?, val authToken: String?
) {
    fun toEntity(): ConnectionSettingsEntity {
        return ConnectionSettingsEntity(
            id = id ?: 0, url = url, apiKey = apiKey, username = username, authToken = authToken
        )
    }

    companion object {
        fun fromEntity(entity: ConnectionSettingsEntity): ConnectionSettings {
            return ConnectionSettings(
                id = entity.id,
                url = entity.url,
                apiKey = entity.apiKey,
                username = entity.username,
                authToken = entity.authToken
            )
        }
    }
}
