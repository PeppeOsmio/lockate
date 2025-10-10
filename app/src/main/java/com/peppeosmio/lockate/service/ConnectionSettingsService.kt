package com.peppeosmio.lockate.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.peppeosmio.lockate.dao.ConnectionSettingsDao
import com.peppeosmio.lockate.data.anonymous_group.mappers.ConnectionSettingsMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.ApiKeyRequiredResDto
import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.utils.ErrorHandler
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConnectionSettingsService(
    private val context: Context,
    private val httpClient: HttpClient,
    private val connectionSettingsDao: ConnectionSettingsDao,
    private val keyStoreService: KeyStoreService
) {
    @Throws
    suspend fun isApiAvailable(url: String) {
        checkRequireApiKey(url)
    }

    @Throws
    suspend fun checkRequireApiKey(url: String): Boolean = withContext(Dispatchers.IO) {
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

    @Throws(InvalidApiKeyException::class, UnauthorizedException::class)
    suspend fun testConnectionSettings(credentials: ConnectionSettings) =
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
        ConnectionSettingsMapper.toDomain(connectionSettingsEntity)
    }

    @Throws(ConnectionSettingsNotFoundException::class)
    suspend fun getConnectionSettingsById(connectionSettingsId: Long): ConnectionSettings =
        withContext(Dispatchers.IO) {
            connectionSettingsDao.getConnectionSettingsById(connectionSettingsId)?.let {
                ConnectionSettingsMapper.toDomain(it)
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
            val id = connectionSettingsDao.insertConnectionSettings(ConnectionSettingsMapper.toEntity(connectionSettings))
            val connectionSettingsEntity = connectionSettingsDao.getConnectionSettingsById(id)
                ?: throw ConnectionSettingsNotFoundException()
            ConnectionSettingsMapper.toDomain(connectionSettingsEntity)
        }

    suspend fun listConnectionSettings(): List<ConnectionSettings> = withContext(Dispatchers.IO) {
        connectionSettingsDao.listConnectionSettings().map {
            ConnectionSettingsMapper.toDomain(it)
        }
    }

    suspend fun deleteConnectionSettings(connectionSettingsId: Long) = withContext(Dispatchers.IO) {
        connectionSettingsDao.deleteConnectionSettings(connectionSettingsId)
    }
}
