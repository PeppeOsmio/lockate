package com.peppeosmio.lockate.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.peppeosmio.lockate.dao.ConnectionSettingsDao
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionSettingsEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.ApiKeyRequiredResDto
import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.utils.ErrorHandler
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConnectionSettingsService(
    private val context: Context,
    private val httpClient: HttpClient,
    private val connectionSettingsDao: ConnectionSettingsDao
) {

    @Throws
    private suspend fun checkRequireApiKey(url: String): Boolean = withContext(Dispatchers.IO) {
        val response = httpClient.get {
            url(Url("${url.trimEnd('/')}/api/api-key/required"))
            headers {
                append(HttpHeaders.ContentType, "application/json")
            }
        }
        Log.d("", response.bodyAsText())
        when (response.status.value) {
            200 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(response)
            else -> ErrorHandler.handleGeneric(response)
        }
        val responseBody = response.body<ApiKeyRequiredResDto>()
        responseBody.required
    }

    @Throws
    private suspend fun testCredentials(credentials: ConnectionSettings) =
        withContext(Dispatchers.IO) {
            val response = httpClient.get {
                url(Url("${credentials.url.trimEnd('/')}/api/api-key/test"))
                headers {
                    credentials.apiKey?.let {
                        append("X-API-KEY", it)
                    }
                }
            }
            Log.d("", response.bodyAsText())
            when (response.status.value) {
                200 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                else -> ErrorHandler.handleGeneric(response)
            }
        }

    @Throws(ConnectionSettingsNotFoundException::class)
    suspend fun getSelectedConnectionSettings(): ConnectionSettings = withContext(Dispatchers.IO) {
        val selectedConnectionSettingsId = context.dataStore.data.map {
            it[ConfigSettings.SELECTED_CONNECTION_SETTINGS_ID]
        }.first()
        val connectionSettingsEntity = if (selectedConnectionSettingsId != null) {
            connectionSettingsDao.getConnectionSettingsById(selectedConnectionSettingsId)
        } else {
            connectionSettingsDao.getFirstConnectionSettings()
        }
        if (connectionSettingsEntity == null) {
            throw ConnectionSettingsNotFoundException()
        }
        ConnectionSettings.fromEntity(connectionSettingsEntity)
    }

    @Throws(ConnectionSettingsNotFoundException::class)
    suspend fun getConnectionSettingsById(connectionSettingsId: Long): ConnectionSettings =
        withContext(Dispatchers.IO) {
            connectionSettingsDao.getConnectionSettingsById(connectionSettingsId)?.let {
                ConnectionSettings.fromEntity(it)
            } ?: throw ConnectionSettingsNotFoundException()
        }

    suspend fun saveSelectedConnectionSettingsId(connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ConfigSettings.SELECTED_CONNECTION_SETTINGS_ID] = connectionSettingsId
            }
        }

    suspend fun saveConnectionSettings(connectionSettings: ConnectionSettings): ConnectionSettings =
        withContext(Dispatchers.IO) {
            if (connectionSettings.id != null) {
                throw IllegalArgumentException("id must be null")
            }
            val id = connectionSettingsDao.insertConnectionSettings(connectionSettings.toEntity())
            val connectionSettingsEntity = connectionSettingsDao.getConnectionSettingsById(id)
                ?: throw ConnectionSettingsNotFoundException()
            ConnectionSettings.fromEntity(connectionSettingsEntity)
        }

    suspend fun listConnectionSettings(): List<ConnectionSettings> = withContext(Dispatchers.IO) {
        connectionSettingsDao.listConnectionSettings().map {
            ConnectionSettings.fromEntity(it)
        }
    }

    suspend fun deleteConnectionSettings(connectionSettingsId: Long) = withContext(Dispatchers.IO) {
        connectionSettingsDao.deleteConnectionSettings(connectionSettingsId)
    }
}
