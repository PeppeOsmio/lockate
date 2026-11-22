package com.peppeosmio.lockate.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.anonymous_group.mappers.ConnectionMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.ApiKeyRequiredResDto
import com.peppeosmio.lockate.data.version.ServerVersionDto
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.ServerUrl
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.ServerAlreadyAddedException
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConnectionService(
    private val context: Context,
    private val httpClient: HttpClient,
    private val connectionDao: ConnectionDao,
    private val keyStoreService: KeyStoreService,
) {
    // tryEmit drops what doesn't fit, and a lost ConnectionDeletedEvent leaves the map on a deleted
    // connection
    private val _events =
        MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = Channel.UNLIMITED)
    val events = _events.asSharedFlow()

    @Throws
    suspend fun checkRequireApiKey(url: String): Boolean =
        withContext(Dispatchers.IO) {
            val response = httpClient.get {
                url(Url("${url.trimEnd('/')}/api/v1/api-key/required"))
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                }
            }
            Log.d("", response.bodyAsText())
            when (response.status.value) {
                200 -> Unit
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
                else -> ErrorHandler.handleGeneric(response)
            }
            val responseBody = response.body<ApiKeyRequiredResDto>()
            responseBody.required
        }

    /** Null when the server has no `GET /api/version`: it is older than 0.7.0. */
    suspend fun getServerVersion(url: String, apiKey: String?): ServerVersionDto? =
        withContext(Dispatchers.IO) {
            val response = httpClient.get {
                url(Url("${url.trimEnd('/')}/api/version"))
                headers { apiKey?.let { append("X-API-KEY", it) } }
            }
            when {
                response.status.value == 200 -> Unit
                isMissingEndpoint(response.status.value, response.bodyAsText()) ->
                    return@withContext null
                else -> ErrorHandler.handleGeneric(response)
            }
            response.body<ServerVersionDto>()
        }

    @Throws(InvalidApiKeyException::class, UnauthorizedException::class)
    suspend fun testConnectionSettings(credentials: Connection) =
        withContext(Dispatchers.IO) {
            val response = httpClient.get {
                url(Url("${credentials.url.trimEnd('/')}/api/v1/api-key/test"))
                headers {
                    credentials.apiKey?.let {
                        append("X-API-KEY", it)
                    }
                }
            }
            Log.d("", response.bodyAsText())
            when (response.status.value) {
                200 -> Unit
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
                else -> ErrorHandler.handleGeneric(response)
            }
        }

    @Throws(ConnectionSettingsNotFoundException::class)
    suspend fun getSelectedConnectionSettings(): Connection =
        withContext(Dispatchers.IO) {
            val selectedConnectionId =
                context.dataStore.data
                    .map {
                        it[ConfigSettings.SELECTED_CONNECTION_SETTINGS_ID]
                    }
                    .first()
            var connectionEntity = selectedConnectionId?.let {
                connectionDao.getConnectionSettingsById(it)
            }
            if (connectionEntity == null) {
                connectionEntity = connectionDao.getFirstConnection()
            }
            if (connectionEntity == null) {
                throw ConnectionSettingsNotFoundException()
            }
            ConnectionMapper.toDomain(connectionEntity, keyStoreService)
        }

    @Throws(ConnectionSettingsNotFoundException::class)
    suspend fun getConnectionSettingsById(connectionSettingsId: Long): Connection =
        withContext(Dispatchers.IO) {
            connectionDao.getConnectionSettingsById(connectionSettingsId)?.let {
                ConnectionMapper.toDomain(it, keyStoreService)
            } ?: throw ConnectionSettingsNotFoundException()
        }

    suspend fun saveSelectedConnectionSettingsId(connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ConfigSettings.SELECTED_CONNECTION_SETTINGS_ID] = connectionSettingsId
            }
        }

    suspend fun getLastAGInternalId(connectionId: Long): Long? =
        withContext(Dispatchers.IO) {
            context.dataStore.data.map { it[ConfigSettings.lastAGInternalId(connectionId)] }.first()
        }

    suspend fun saveLastAGInternalId(connectionId: Long, anonymousGroupInternalId: Long) =
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ConfigSettings.lastAGInternalId(connectionId)] = anonymousGroupInternalId
            }
        }

    /**
     * Saves a new server under its normalized address.
     *
     * @throws ServerAlreadyAddedException when that address is already saved
     */
    suspend fun saveConnectionSettings(connection: Connection): Connection =
        withContext(Dispatchers.IO) {
            require(connection.id == null) { "id must be null" }
            val url = ServerUrl.normalize(connection.url)
            connectionDao.getConnectionByUrl(url)?.let {
                throw ServerAlreadyAddedException(it.id)
            }
            val id =
                connectionDao.insertConnection(
                    ConnectionMapper.toEntity(connection.copy(url = url), keyStoreService)
                )
            getConnectionSettingsById(id)
        }

    /** @throws ServerAlreadyAddedException when the new address is another saved server's */
    suspend fun updateConnection(connection: Connection): Connection =
        withContext(Dispatchers.IO) {
            val url = ServerUrl.normalize(connection.url)
            connectionDao.getConnectionByUrl(url)?.let {
                if (it.id != connection.id) throw ServerAlreadyAddedException(it.id)
            }
            val normalized = connection.copy(url = url)
            connectionDao.updateConnection(ConnectionMapper.toEntity(normalized, keyStoreService))
            normalized
        }

    /** The saved server with this address, whatever way it is written, or null */
    suspend fun findByUrl(url: String): Connection? =
        withContext(Dispatchers.IO) {
            connectionDao.getConnectionByUrl(ServerUrl.normalize(url))?.let {
                ConnectionMapper.toDomain(it, keyStoreService)
            }
        }

    /** How many groups this phone has on each server, by server id */
    suspend fun countGroupsByServer(): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            connectionDao.countGroups().associate { it.connectionId to it.groupCount }
        }

    suspend fun listConnectionSettings(): List<Connection> =
        withContext(Dispatchers.IO) {
            connectionDao.listConnections().map {
                ConnectionMapper.toDomain(it, keyStoreService)
            }
        }

    suspend fun deleteConnection(connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {
            connectionDao.deleteConnectionSettings(connectionSettingsId)
            _events.tryEmit(ConnectionEvent.ConnectionDeletedEvent(connectionSettingsId))
        }
}

/**
 * Whether the server answered that it has no such endpoint. Servers older than 0.7.1 answer an
 * unknown path with 500 "No static resource …" instead of 404.
 */
internal fun isMissingEndpoint(status: Int, body: String): Boolean =
    status == 404 || (status == 500 && body.contains("No static resource"))
