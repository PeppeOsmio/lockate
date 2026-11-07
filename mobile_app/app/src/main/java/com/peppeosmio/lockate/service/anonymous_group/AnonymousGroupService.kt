package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.mappers.CircleMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.AGFrameDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberMessageReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.CircleUpsertReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.ChannelState
import com.peppeosmio.lockate.domain.anonymous_group.ChannelStatus
import com.peppeosmio.lockate.domain.anonymous_group.GroupFrame
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.geofence.GeofenceTracker
import com.peppeosmio.lockate.service.geofence.CircleEvents
import com.peppeosmio.lockate.service.geofence.circleEventTexts
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGMemberMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.LocationRecordMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinResDto
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.SendLocationStatus
import com.peppeosmio.lockate.exceptions.AGMemberNotAdminException
import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.LocationTimeoutException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.ConnectivityService
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.location.PositionProvider
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.time.inMs
import java.net.ConnectException
import kotlin.io.encoding.Base64
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal enum class StreamRetryAction { Stop, AwaitNetwork, Backoff }

/**
 * Decides what the group channel does after it ended: a missing network parks on the connectivity
 * event, everything else but errors that retrying can't fix uses a timed backoff.
 */
internal fun classifyStreamFailure(e: Throwable?, isNetworkAvailable: Boolean): StreamRetryAction = when {
    e is UnauthorizedException || e is RemoteAGNotFoundException || e is InvalidApiKeyException -> StreamRetryAction.Stop
    !isNetworkAvailable -> StreamRetryAction.AwaitNetwork
    else -> StreamRetryAction.Backoff
}

internal fun nextBackoffSeconds(retries: Int): Long = min(5L * retries, 60L)

private const val MEMBER_REMOVED_CLOSE_CODE: Short = 4401
private const val GROUP_DELETED_CLOSE_CODE: Short = 4404

class AnonymousGroupService(
    private val anonymousGroupDao: AnonymousGroupDao,
    private val cryptoService: CryptoService,
    private val connectionService: ConnectionService,
    private val httpClient: HttpClient,
    private val locationService: LocationService,
    private val positionProvider: PositionProvider,
    private val connectivityService: ConnectivityService,
    private val keyStoreService: KeyStoreService,
    private val payloadCodec: PayloadCodec,
    private val sendLoopLog: SendLoopLog
) {
    private val _events = MutableSharedFlow<AnonymousGroupEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    suspend fun listLocalAnonymousGroups(connectionSettingsId: Long): List<AnonymousGroup> =
        withContext(Dispatchers.IO) {
            val entities = anonymousGroupDao.listAnonymousGroupsOfConnection(connectionSettingsId)
            entities.map {
                AnonymousGroupMapper.toDomain(
                    entity = it, keyStoreService = keyStoreService
                )
            }
        }

    suspend fun getAGByInternalId(anonymousGroupInternalId: Long): AnonymousGroup =
        withContext(Dispatchers.IO) {
            val entity = anonymousGroupDao.getAGByInternalId(
                anonymousGroupInternalId
            ) ?: throw LocalAGNotFoundException()
            AnonymousGroupMapper.toDomain(
                entity = entity, keyStoreService = keyStoreService
            )
        }

    suspend fun getLocalAGMembers(anonymousGroup: AnonymousGroup): List<AGMember> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.getAGByInternalId(anonymousGroup.internalId)
                ?: throw LocalAGNotFoundException()
            anonymousGroupDao.listAGMembers(anonymousGroup.internalId).map {
                AGMemberMapper.entityToDomain(it)
            }
        }

    suspend fun deleteLocalAnonymousGroup(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.deleteAGByInternalId(anonymousGroup.internalId)
            _events.tryEmit(
                AnonymousGroupEvent.DeleteAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        }

    suspend fun setAGSendLocation(anonymousGroup: AnonymousGroup, sendLocation: Boolean) {
        anonymousGroupDao.setAGSendLocation(
            anonymousGroupInternalId = anonymousGroup.internalId, sendLocation = sendLocation
        )
        if (sendLocation) {
            _events.tryEmit(
                AnonymousGroupEvent.SendEnabledEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        } else {
            _events.tryEmit(
                AnonymousGroupEvent.SendDisabledEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        }
    }

    private suspend fun setAGIsMemberFalse(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            Log.d("setAGIsMemberFalse", "Setting false")
            anonymousGroupDao.setAGIsMember(
                anonymousGroupInternalId = anonymousGroup.internalId, isMember = false
            )
            _events.tryEmit(
                AnonymousGroupEvent.RemovedFromAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }

    private suspend fun setAGIsMemberTrue(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGIsMember(
                anonymousGroupInternalId = anonymousGroup.internalId, isMember = true
            )
            _events.tryEmit(
                AnonymousGroupEvent.ReaddedToAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        }

    private suspend fun setAGExistsRemoteFalse(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGExistsRemote(
                anonymousGroupInternalId = anonymousGroup.internalId, existsRemote = false
            )
            _events.tryEmit(
                AnonymousGroupEvent.RemoteAGDoesntExistEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        }

    private suspend fun setAGExistsRemoteTrue(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGExistsRemote(
                anonymousGroupInternalId = anonymousGroup.internalId, existsRemote = true
            )
            _events.tryEmit(
                AnonymousGroupEvent.RemoteAGExistsEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId
                )
            )
        }

    @Throws(
        UnauthorizedException::class,
        InvalidApiKeyException::class,
        RemoteAGNotFoundException::class,
        APIException::class
    )
    suspend fun leaveAnonymousGroup(anonymousGroupInternalId: Long, connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {

            val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )

            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val response = httpClient.post {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/leave")
                headers {
                    connectionSettings.apiKey?.let { ak -> append(name = "X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                    )
                    append(HttpHeaders.ContentType, "application/json")
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401, 403 -> {
                        ErrorHandler.handleUnauthorized(response)
                    }

                    404 -> throw RemoteAGNotFoundException()

                    else -> ErrorHandler.handleGeneric(response)
                }
            } catch (e: RemoteAGNotFoundException) {
                deleteLocalAnonymousGroup(anonymousGroup)
                throw e
            } catch (e: UnauthorizedException) {
                setAGIsMemberFalse(anonymousGroup)
                throw AGMemberUnauthorizedException()
            }
            deleteLocalAnonymousGroup(anonymousGroup)
        }


    @Throws(
        ConnectionSettingsNotFoundException::class,
        SerializationException::class,
        UnauthorizedException::class,
        ConnectException::class
    )
    suspend fun createAnonymousGroup(
        connectionId: Long,
        groupName: String,
        memberName: String,
    ): AnonymousGroup = withContext(Dispatchers.IO) {
        val key = cryptoService.generateGroupKey()
        val encryptedMemberName = cryptoService.encrypt(
            data = memberName.toByteArray(Charsets.UTF_8), key = key
        )
        val encryptedMemberNameDto = EncryptedDataMapper.toDto(encryptedMemberName)
        val connectionSettings = connectionService.getConnectionSettingsById(connectionId)
        val encryptedGroupNameDto = EncryptedDataMapper.toDto(
            cryptoService.encrypt(
                data = groupName.toByteArray(Charsets.UTF_8), key = key
            )
        )

        val createResponse = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(
                AGCreateReqDto(
                    encryptedMemberName = encryptedMemberNameDto,
                    encryptedGroupName = encryptedGroupNameDto,
                )
            )
        }

        when (createResponse.status.value) {
            201 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(createResponse)
            else -> ErrorHandler.handleGeneric(createResponse)
        }

        val createResponseBody = createResponse.body<AGCreateResDto>()
        val agMember = AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = createResponseBody.authenticatedMemberInfo.member,
            cryptoService = cryptoService,
            payloadCodec = payloadCodec,
            key = key,
            groupId = createResponseBody.anonymousGroup.id
        )
        val agMemberEntity = AGMemberMapper.domainToEntity(
            agMember = agMember,
            anonymousGroupInternalId = 0,
        )
        val anonymousGroup = AnonymousGroup(
            internalId = 0,
            id = createResponseBody.anonymousGroup.id,
            name = groupName,
            createdAt = createResponseBody.anonymousGroup.createdAt,
            joinedAt = createResponseBody.authenticatedMemberInfo.member.createdAt,
            memberName = memberName,
            memberId = agMemberEntity.id,
            memberToken = Base64.decode(createResponseBody.authenticatedMemberInfo.token),
            memberIsAGAdmin = createResponseBody.authenticatedMemberInfo.member.isAGAdmin, // should be true
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = key,
            connectionId = connectionId
        )
        val anonymousGroupInternalId = anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupMapper.toEntity(
                anonymousGroup = anonymousGroup, keyStoreService = keyStoreService
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.tryEmit(
            AnonymousGroupEvent.NewAnonymousGroupEvent(
                anonymousGroupInternalId = anonymousGroupInternalId,
                anonymousGroupId = anonymousGroup.id,
                connectionId = anonymousGroup.connectionId
            )
        )
        AnonymousGroup(
            internalId = anonymousGroupInternalId,
            id = createResponseBody.anonymousGroup.id,
            name = groupName,
            createdAt = createResponseBody.anonymousGroup.createdAt,
            joinedAt = createResponseBody.authenticatedMemberInfo.member.createdAt,
            memberName = memberName,
            memberId = createResponseBody.authenticatedMemberInfo.member.id,
            memberToken = Base64.decode(createResponseBody.authenticatedMemberInfo.token),
            memberIsAGAdmin = createResponseBody.authenticatedMemberInfo.member.isAGAdmin, // should be true
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = key,
            connectionId = connectionId
        )
    }

    /**
     * Joins with the credentials an invite delivered. The group name is decrypted with [groupKey]
     * before anything is stored, so a wrong key never ends up on the device.
     */
    @Throws(
        ConnectionSettingsNotFoundException::class,
        SerializationException::class,
        RemoteAGNotFoundException::class,
        UnauthorizedException::class,
        LocalAGExistsException::class,
        ConnectException::class
    )
    suspend fun joinWithInvite(
        connectionId: Long,
        anonymousGroupId: String,
        groupKey: ByteArray,
        joinToken: ByteArray,
        memberName: String
    ): AnonymousGroup = withContext(Dispatchers.IO) {
        if (anonymousGroupDao.getAGByIdAndConnectionId(
                anonymousGroupId = anonymousGroupId, connectionId = connectionId
            ) != null
        ) {
            throw LocalAGExistsException()
        }
        val connectionSettings = connectionService.getConnectionSettingsById(connectionId)
        val encryptedMemberName = cryptoService.encrypt(
            data = memberName.toByteArray(Charsets.UTF_8), key = groupKey
        )
        val response = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/join")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(
                AGJoinReqDto(
                    joinToken = Base64.encode(joinToken),
                    encryptedMemberName = EncryptedDataMapper.toDto(encryptedMemberName)
                )
            )
        }
        when (response.status.value) {
            200 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(response)
            404 -> throw RemoteAGNotFoundException()
            else -> ErrorHandler.handleGeneric(response)
        }
        val body = response.body<AGJoinResDto>()
        val groupName = cryptoService.decrypt(
            encryptedData = EncryptedDataMapper.toDomain(body.anonymousGroup.encryptedName),
            key = groupKey
        ).toString(Charsets.UTF_8)
        val agMember = AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = body.authenticatedMemberInfo.member,
            cryptoService = cryptoService,
            payloadCodec = payloadCodec,
            key = groupKey,
            groupId = body.anonymousGroup.id
        )
        val agMemberEntity = AGMemberMapper.domainToEntity(
            agMember = agMember, anonymousGroupInternalId = 0
        )
        val anonymousGroup = AnonymousGroup(
            internalId = 0,
            id = body.anonymousGroup.id,
            name = groupName,
            createdAt = body.anonymousGroup.createdAt,
            joinedAt = body.authenticatedMemberInfo.member.createdAt,
            memberName = memberName,
            memberId = agMemberEntity.id,
            memberToken = Base64.decode(body.authenticatedMemberInfo.token),
            memberIsAGAdmin = body.authenticatedMemberInfo.member.isAGAdmin,
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = groupKey,
            connectionId = connectionId
        )
        val anonymousGroupInternalId = anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupMapper.toEntity(
                anonymousGroup = anonymousGroup, keyStoreService = keyStoreService
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.tryEmit(
            AnonymousGroupEvent.NewAnonymousGroupEvent(
                anonymousGroupInternalId = anonymousGroupInternalId,
                anonymousGroupId = anonymousGroup.id,
                connectionId = connectionId
            )
        )
        anonymousGroup.copy(internalId = anonymousGroupInternalId)
    }

    suspend fun verifyMemberAuth(connectionSettingsId: Long, anonymousGroupInternalId: Long) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/auth/verify")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                    )
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401, 403 -> ErrorHandler.handleUnauthorized(response)
                    404 -> throw RemoteAGNotFoundException()
                    else -> ErrorHandler.handleGeneric(response)
                }
                // just if it was marked as not existing by mistake
                if (!anonymousGroup.existsRemote) {
                    setAGExistsRemoteTrue(anonymousGroup)
                }
                // just if it was marked as not a member by mistake
                if (!anonymousGroup.isMember) {
                    setAGIsMemberTrue(anonymousGroup)
                }
            } catch (e: UnauthorizedException) {
                setAGIsMemberFalse(anonymousGroup)
                throw AGMemberUnauthorizedException()
            } catch (e: RemoteAGNotFoundException) {
                setAGExistsRemoteFalse(anonymousGroup)
                throw e
            }
        }

    suspend fun leaveAllAG(connectionSettingsId: Long) {
        val ags = listLocalAnonymousGroups(connectionSettingsId)
        ags.forEach {
            leaveAnonymousGroup(
                anonymousGroupInternalId = it.internalId,
                connectionSettingsId = connectionSettingsId
            )
        }
    }

    suspend fun deleteAllAG(connectionSettingsId: Long) {
        anonymousGroupDao.deleteAllAGsOfConnection(connectionSettingsId)
    }

    private suspend fun getAvailableConnections(
        connectionList: List<Connection>,
    ): List<Connection> = coroutineScope {
        connectionList.map { settings ->
            async {
                try {
                    connectionService.isApiAvailable(settings.url)
                    settings
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun getAvailableAGs(availableConnections: List<Connection>): Int {
        var availableAGs = 0
        availableConnections.forEach {
            availableAGs += anonymousGroupDao.listAGToSendLocationOfConnection(it.id!!).size
        }
        return availableAGs
    }

    private val channelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelsMutex = Mutex()
    private val channels = mutableMapOf<String, GroupChannel>()
    private val _groupFrames = MutableSharedFlow<GroupFrame>(extraBufferCapacity = 64)

    /** What happens in the groups whose channel is open, already applied to the local database. */
    val groupFrames = _groupFrames.asSharedFlow()

    private class GroupChannel {
        lateinit var job: Job
        var leases = 0
        val sendLeases = MutableStateFlow(0)
        val status = MutableStateFlow(ChannelStatus())
    }

    /**
     * Keeps the group's websocket open for as long as the caller is suspended here, sharing it with
     * whoever else holds the group: the foreground service holds every group, the open map holds
     * the one it shows. The location is sent while at least one holder asks for it with [send].
     * [onStatus] gets the state of the connection and of the location.
     */
    suspend fun holdChannel(
        anonymousGroup: AnonymousGroup,
        send: Boolean,
        onStatus: (ChannelStatus) -> Unit = {}
    ): Nothing {
        val channel = channelsMutex.withLock {
            val channel = channels.getOrPut(anonymousGroup.id) {
                GroupChannel().also { c ->
                    c.job = channelScope.launch { runChannel(anonymousGroup, c) }
                }
            }
            channel.leases++
            if (send) channel.sendLeases.update { it + 1 }
            channel
        }
        try {
            channel.status.collect { onStatus(it) }
        } finally {
            withContext(NonCancellable) {
                channelsMutex.withLock {
                    if (send) channel.sendLeases.update { it - 1 }
                    if (--channel.leases == 0) {
                        channel.job.cancel()
                        channels.remove(anonymousGroup.id)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runChannel(anonymousGroup: AnonymousGroup, channel: GroupChannel) {
        val connectionSettings =
            connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        // outlives the connections, so a reconnect's snapshot can't rewind the members it knows
        val tracker = GeofenceTracker()
        val maxRetries = 10
        var retries = 0
        val tag = "channel ${anonymousGroup.id}"
        while (true) {
            var isConnected = false
            try {
                sendLoopLog.log("$tag connecting")
                var removed = false
                httpClient.webSocket(request = {
                    url("${connectionSettings.getWebSocketUrl()}/api/ws/anonymous-groups/${anonymousGroup.id}")
                    headers {
                        connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                        append(
                            HttpHeaders.Authorization, "AGMember ${anonymousGroup.memberId} ${
                                Base64.encode(anonymousGroup.memberToken)
                            }"
                        )
                    }
                }) {
                    isConnected = true
                    retries = 0
                    channel.status.update { it.copy(state = ChannelState.Connected) }
                    sendLoopLog.log("$tag connected")
                    coroutineScope {
                        val sender = launch {
                            channel.sendLeases.map { it > 0 }.distinctUntilChanged()
                                .collectLatest { sending ->
                                    if (sending) {
                                        sendLocationWhileEnabled(
                                            anonymousGroup, channel, this@webSocket
                                        )
                                    }
                                }
                        }
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            handleFrame(
                                anonymousGroup = anonymousGroup,
                                dto = Json.decodeFromString<AGFrameDto>(frame.readText()),
                                tracker = tracker
                            )
                        }
                        sender.cancel()
                    }
                    when (closeReason.await()?.code) {
                        MEMBER_REMOVED_CLOSE_CODE -> {
                            sendLoopLog.log("$tag stopped: removed")
                            setAGIsMemberFalse(anonymousGroup)
                            removed = true
                        }

                        GROUP_DELETED_CLOSE_CODE -> {
                            sendLoopLog.log("$tag stopped: group deleted")
                            setAGExistsRemoteFalse(anonymousGroup)
                            removed = true
                        }
                    }
                }
                if (removed) {
                    channel.status.update { it.copy(state = ChannelState.Stopped) }
                    return
                }
                throw IOException("Channel closed by the server")
            } catch (e: Exception) {
                if (e is CancellationException) {
                    sendLoopLog.log("$tag cancelled")
                    throw e
                }
                e.printStackTrace()
                sendLoopLog.log("$tag failed ${e::class.simpleName} connected=$isConnected")
                val failure = if (e is ResponseException) {
                    try {
                        when (e.response.status.value) {
                            401, 403 -> ErrorHandler.handleUnauthorized(e.response)
                            404 -> throw RemoteAGNotFoundException()
                            else -> ErrorHandler.handleGeneric(e.response)
                        }
                        e
                    } catch (ee: Exception) {
                        ee
                    }
                } else e
                when (failure) {
                    is UnauthorizedException -> setAGIsMemberFalse(anonymousGroup)
                    is RemoteAGNotFoundException -> setAGExistsRemoteFalse(anonymousGroup)
                }
                val action = classifyStreamFailure(failure, connectivityService.isNetworkAvailable())
                if (action == StreamRetryAction.Stop) {
                    sendLoopLog.log("$tag stopped: ${failure::class.simpleName}")
                    channel.status.update { it.copy(state = ChannelState.Stopped) }
                    return
                }
                channel.status.update { it.copy(state = ChannelState.Reconnecting) }
                sendLoopLog.log("$tag waiting $action")
                when (action) {
                    StreamRetryAction.AwaitNetwork -> {
                        // reconnect the instant the network returns
                        withTimeoutOrNull(60.seconds) { connectivityService.awaitNetworkAvailable() }
                    }

                    else -> {
                        retries = min(retries + 1, maxRetries)
                        val waitSeconds = nextBackoffSeconds(retries)
                        Log.e(
                            "",
                            "Can't connect to ${connectionSettings.url} retrying in $waitSeconds s"
                        )
                        delay(waitSeconds.seconds)
                    }
                }
                sendLoopLog.log("$tag wait over")
            }
        }
    }

    /**
     * Sends the location on the open channel. GPS faults don't close the channel, which keeps
     * receiving: they are reported in the channel's status and the sending resumes by itself.
     */
    private suspend fun sendLocationWhileEnabled(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        session: DefaultClientWebSocketSession,
    ) {
        try {
            while (true) {
                try {
                    locationService.checkPermissionsAndLocationEnabled()
                    channel.status.update { it.copy(isLocationDisabled = false) }
                    sendLocation(anonymousGroup, channel, session)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    when (e) {
                        is LocationDisabledException, is NoPermissionException -> {
                            channel.status.update { it.copy(isLocationDisabled = true) }
                            // the timeout is a safety net so we re-check even if the signal never arrives
                            withTimeoutOrNull(60.seconds) { locationService.awaitLocationEnabled() }
                        }

                        is LocationTimeoutException -> {
                            channel.status.update { it.copy(isLocationUnavailable = true) }
                            delay(5.seconds)
                        }

                        else -> throw e
                    }
                }
            }
        } finally {
            channel.status.update {
                it.copy(isLocationDisabled = false, isLocationUnavailable = false)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun sendLocation(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        session: DefaultClientWebSocketSession,
    ) = coroutineScope {
        Log.i(
            "",
            "Sending AG location agId: ${anonymousGroup.id} agMemberId: ${anonymousGroup.memberId}"
        )
        suspend fun getTimeoutJob() {
            delay(30000L)
            Log.e("", "No location obtained in the last 30 s")
            throw LocationTimeoutException()
        }

        var lastSent: Instant? = null
        var lastMode: Pair<UserActivity, Boolean>? = null

        var timeoutJob = launch {
            getTimeoutJob()
        }
        // location can be disabled while the socket stays open; fused updates just
        // stop silently, so watch for it and surface it immediately instead of
        // waiting out the 30s timeout.
        launch {
            locationService.awaitLocationDisabled()
            throw LocationDisabledException()
        }
        positionProvider.positions().collect { position ->
            val now = Clock.System.now()
            val coordinates = position.fix.coordinates
            val mode = position.activity to position.isFresh
            if (mode != lastMode) {
                lastMode = mode
                sendLoopLog.log("channel ${anonymousGroup.id} activity=${mode.first} fresh=${mode.second}")
            }

            lastSent?.let {
                if ((now - it).inMs < 1500) {
                    return@collect
                }
            }
            lastSent = now

            channel.status.update { it.copy(isLocationUnavailable = false) }

            timeoutJob.cancel()
            timeoutJob = launch {
                getTimeoutJob()
            }
            val encrypted = payloadCodec.encryptMemberMessage(
                message = MemberMessage(coordinates = coordinates, activity = position.activity),
                key = anonymousGroup.key,
                groupId = anonymousGroup.id,
                memberId = anonymousGroup.memberId
            )
            session.send(
                Frame.Text(
                    text = Json.encodeToString(
                        AGMemberMessageReqDto(
                            encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                            encryptedActivity = EncryptedDataMapper.toDto(encrypted.activity)
                        )
                    )
                )
            )
            _events.tryEmit(
                AnonymousGroupEvent.AGLocationSentEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                    locationRecord = LocationRecord(
                        coordinates = coordinates,
                        timestamp = Clock.System.now().toLocalDateTime(TimeZone.UTC),
                        activity = position.activity
                    )
                )
            )
        }
    }

    private suspend fun decryptMember(
        anonymousGroup: AnonymousGroup, dto: EncryptedAGMemberDto
    ): AGMember = AGMemberMapper.dtoToDomain(
        encryptedAGMemberDto = dto,
        cryptoService = cryptoService,
        payloadCodec = payloadCodec,
        key = anonymousGroup.key,
        groupId = anonymousGroup.id
    )

    private suspend fun handleFrame(
        anonymousGroup: AnonymousGroup,
        dto: AGFrameDto,
        tracker: GeofenceTracker,
    ) {
        val internalId = anonymousGroup.internalId
        val frame: AGChannelFrame = try {
            when (dto.type) {
                AGFrameDto.SNAPSHOT -> {
                    val members = dto.members!!.mapDecryptable(onUndecryptable = {
                        Log.e("", "Skipped member ${it.id} of ${anonymousGroup.id}: can't decrypt it")
                    }) { decryptMember(anonymousGroup, it) }
                    val circles = dto.circles!!.mapDecryptable(onUndecryptable = {
                        Log.e("", "Skipped circle ${it.id} of ${anonymousGroup.id}: can't decrypt it")
                    }) {
                        CircleMapper.dtoToDomain(it, payloadCodec, anonymousGroup.key, anonymousGroup.id)
                    }
                    members.firstOrNull { it.id == anonymousGroup.memberId }?.let { me ->
                        if (me.isAGAdmin != anonymousGroup.memberIsAGAdmin) {
                            anonymousGroupDao.setAGMemberIsAGAdminSelf(internalId, me.isAGAdmin)
                        }
                    }
                    anonymousGroupDao.setAGMembers(
                        anonymousGroupInternalId = internalId,
                        agMemberEntities = members.map {
                            AGMemberMapper.domainToEntity(it, anonymousGroupInternalId = internalId)
                        })
                    anonymousGroupDao.syncCircles(internalId, circles)
                    tracker.replaceCircles(circles)
                    val others = members.filter { it.id != anonymousGroup.memberId }
                    tracker.retainMembers(others.map { it.id }.toSet())
                    others.forEach { member ->
                        member.lastLocationRecord?.let {
                            tracker.seedMember(member.id, it.coordinates, it.timestamp)
                        }
                    }
                    AGChannelFrame.Snapshot(members, circles)
                }

                AGFrameDto.MEMBER_JOINED -> {
                    val member = decryptMember(anonymousGroup, dto.member!!)
                    if (anonymousGroupDao.getAGMemberByIdAndAGInternalId(member.id, internalId) == null) {
                        anonymousGroupDao.insertAGMember(
                            AGMemberMapper.domainToEntity(member, anonymousGroupInternalId = internalId)
                        )
                    }
                    if (member.id != anonymousGroup.memberId) {
                        member.lastLocationRecord?.let {
                            tracker.seedMember(member.id, it.coordinates, it.timestamp)
                        }
                    }
                    AGChannelFrame.MemberJoined(member)
                }

                AGFrameDto.MEMBER_LEFT -> {
                    anonymousGroupDao.deleteAGMemberById(dto.memberId!!, internalId)
                    tracker.removeMember(dto.memberId)
                    AGChannelFrame.MemberLeft(dto.memberId)
                }

                AGFrameDto.MEMBER_UPDATED -> {
                    anonymousGroupDao.setAGMemberIsAGAdmin(dto.memberId!!, internalId, dto.isAGAdmin!!)
                    if (dto.memberId == anonymousGroup.memberId) {
                        anonymousGroupDao.setAGMemberIsAGAdminSelf(internalId, dto.isAGAdmin)
                    }
                    AGChannelFrame.MemberUpdated(dto.memberId, dto.isAGAdmin)
                }

                AGFrameDto.MEMBER_MESSAGE -> {
                    val memberId = dto.memberId!!
                    val locationRecord = LocationRecordMapper.toDomain(
                        encryptedLocationRecordDto = dto.record!!,
                        payloadCodec = payloadCodec,
                        key = anonymousGroup.key,
                        groupId = anonymousGroup.id,
                        memberId = memberId
                    )
                    // a member unknown locally joined after our last snapshot: its joined frame
                    // brings it in
                    anonymousGroupDao.getAGMemberByIdAndAGInternalId(memberId, internalId)
                        ?.let { agMember ->
                            anonymousGroupDao.setAGMemberLastLocation(
                                agMemberInternalId = agMember.internalId,
                                lastLatitude = locationRecord.coordinates.latitude,
                                lastLongitude = locationRecord.coordinates.longitude,
                                lastSeen = locationRecord.timestamp.toInstant(TimeZone.UTC)
                                    .toEpochMilliseconds(),
                                lastActivity = locationRecord.activity.name
                            )
                        }
                    // the server never relays our own messages, but if it did they mustn't tell us
                    // about ourselves
                    val transitions = if (memberId == anonymousGroup.memberId) {
                        emptyList()
                    } else {
                        tracker.update(memberId, locationRecord.coordinates, locationRecord.timestamp)
                    }
                    AGChannelFrame.LiveLocation(AGLocationUpdate(locationRecord, memberId, transitions))
                }

                AGFrameDto.CIRCLE_UPSERTED -> {
                    val circle = CircleMapper.dtoToDomain(
                        dto.circle!!, payloadCodec, anonymousGroup.key, anonymousGroup.id
                    )
                    anonymousGroupDao.upsertCircle(internalId, circle)
                    tracker.upsertCircle(circle)
                    AGChannelFrame.CircleUpserted(circle)
                }

                AGFrameDto.CIRCLE_DELETED -> {
                    anonymousGroupDao.deleteCircle(dto.circleId!!, internalId)
                    tracker.removeCircle(dto.circleId)
                    AGChannelFrame.CircleDeleted(dto.circleId)
                }

                else -> return
            }
        } catch (e: CryptoException) {
            Log.e("", "Can't decrypt a ${dto.type} frame of ${anonymousGroup.id}")
            return
        }
        _groupFrames.emit(GroupFrame(internalId, anonymousGroup.id, frame))
    }

    /**
     * Holds the channel of every group we are in, for the foreground service: the groups we share
     * the location with, and the others to be told about the circles.
     */
    suspend fun holdAllChannels(onStatusUpdate: (SendLocationStatus) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val jobs = mutableMapOf<String, Job>()
            val statuses = mutableMapOf<String, Pair<Boolean, ChannelStatus>>()

            fun emitStatus() {
                val snapshot = synchronized(statuses) { statuses.values.toList() }
                val sending = snapshot.filter { it.first }
                onStatusUpdate(
                    SendLocationStatus(
                        totalAGCount = sending.size,
                        activeAGCount = sending.count { it.second.state == ChannelState.Connected },
                        receivingAGCount = snapshot.size - sending.size,
                        isLocationDisabled = sending.any { it.second.isLocationDisabled },
                        isLocationUnavailable = sending.any { it.second.isLocationUnavailable }
                    )
                )
            }

            suspend fun start(entity: AnonymousGroupEntity) {
                val anonymousGroup = AnonymousGroupMapper.toDomain(
                    entity = entity, keyStoreService = keyStoreService
                )
                jobs.remove(anonymousGroup.id)?.cancel()
                synchronized(statuses) {
                    statuses[anonymousGroup.id] = anonymousGroup.sendLocation to ChannelStatus()
                }
                jobs[anonymousGroup.id] = launch {
                    holdChannel(anonymousGroup, send = anonymousGroup.sendLocation) { status ->
                        synchronized(statuses) {
                            statuses[anonymousGroup.id] = anonymousGroup.sendLocation to status
                        }
                        emitStatus()
                    }
                }
                emitStatus()
            }

            fun stop(anonymousGroupId: String) {
                jobs.remove(anonymousGroupId)?.cancel()
                synchronized(statuses) { statuses.remove(anonymousGroupId) }
                emitStatus()
            }

            suspend fun startByInternalId(anonymousGroupInternalId: Long) {
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)?.let { start(it) }
            }

            anonymousGroupDao.listAGsToHold().forEach { start(it) }
            emitStatus()

            events.collect { event ->
                when (event) {
                    is AnonymousGroupEvent.NewAnonymousGroupEvent -> startByInternalId(event.anonymousGroupInternalId)
                    is AnonymousGroupEvent.ReaddedToAnonymousGroupEvent -> startByInternalId(event.anonymousGroupInternalId)
                    is AnonymousGroupEvent.RemoteAGExistsEvent -> startByInternalId(event.anonymousGroupInternalId)
                    is AnonymousGroupEvent.SendEnabledEvent -> startByInternalId(event.anonymousGroupInternalId)
                    is AnonymousGroupEvent.SendDisabledEvent -> startByInternalId(event.anonymousGroupInternalId)
                    is AnonymousGroupEvent.DeleteAnonymousGroupEvent -> stop(event.anonymousGroupId)
                    is AnonymousGroupEvent.RemovedFromAnonymousGroupEvent -> stop(event.anonymousGroupId)
                    is AnonymousGroupEvent.RemoteAGDoesntExistEvent -> stop(event.anonymousGroupId)
                    else -> Unit
                }
            }
        }

    private suspend fun circleRequest(
        connectionSettingsId: Long,
        anonymousGroupInternalId: Long,
        circleId: String,
        method: HttpMethod,
        body: CircleUpsertReqDto?
    ) {
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val response = httpClient.request {
            this.method = method
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/circles/$circleId")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(
                    HttpHeaders.Authorization,
                    "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                )
            }
            body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
        try {
            when (response.status.value) {
                200 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
        } catch (e: UnauthorizedException) {
            setAGIsMemberFalse(anonymousGroup)
            throw AGMemberUnauthorizedException()
        } catch (e: RemoteAGNotFoundException) {
            setAGExistsRemoteFalse(anonymousGroup)
            throw e
        }
    }

    /** Asks the map to move to [circle]. */
    fun showCircleOnMap(anonymousGroup: AnonymousGroup, circle: Circle) {
        _events.tryEmit(
            AnonymousGroupEvent.ShowCircleOnMapEvent(
                anonymousGroupInternalId = anonymousGroup.internalId,
                anonymousGroupId = anonymousGroup.id,
                connectionId = anonymousGroup.connectionId,
                circle = circle
            )
        )
    }

    suspend fun listCircles(anonymousGroupInternalId: Long): List<Circle> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.listCircles(anonymousGroupInternalId).map { CircleMapper.entityToDomain(it) }
        }

    suspend fun upsertCircle(
        connectionSettingsId: Long, anonymousGroup: AnonymousGroup, circle: Circle
    ) = withContext(Dispatchers.IO) {
        val encrypted = payloadCodec.encryptCircle(circle, anonymousGroup.key, anonymousGroup.id)
        circleRequest(
            connectionSettingsId = connectionSettingsId,
            anonymousGroupInternalId = anonymousGroup.internalId,
            circleId = circle.id,
            method = HttpMethod.Put,
            body = CircleUpsertReqDto(
                encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                encryptedRadius = EncryptedDataMapper.toDto(encrypted.radius),
                encryptedName = EncryptedDataMapper.toDto(encrypted.name)
            )
        )
        anonymousGroupDao.upsertCircle(anonymousGroup.internalId, circle)
        _groupFrames.emit(
            GroupFrame(
                anonymousGroup.internalId, anonymousGroup.id, AGChannelFrame.CircleUpserted(circle)
            )
        )
    }

    suspend fun deleteCircle(
        connectionSettingsId: Long, anonymousGroup: AnonymousGroup, circleId: String
    ) = withContext(Dispatchers.IO) {
        circleRequest(
            connectionSettingsId = connectionSettingsId,
            anonymousGroupInternalId = anonymousGroup.internalId,
            circleId = circleId,
            method = HttpMethod.Delete,
            body = null
        )
        anonymousGroupDao.deleteCircle(circleId, anonymousGroup.internalId)
        _groupFrames.emit(
            GroupFrame(
                anonymousGroup.internalId, anonymousGroup.id, AGChannelFrame.CircleDeleted(circleId)
            )
        )
    }

    /** The events to notify for a live frame: one text per transition, with the group and the member. */
    suspend fun describeCircleEvents(groupFrame: GroupFrame): CircleEvents? =
        withContext(Dispatchers.IO) {
            val update = (groupFrame.frame as? AGChannelFrame.LiveLocation)?.update ?: return@withContext null
            if (update.transitions.isEmpty()) return@withContext null
            val group = anonymousGroupDao.getAGByInternalId(groupFrame.anonymousGroupInternalId)
                ?: return@withContext null
            val member = anonymousGroupDao.getAGMemberByIdAndAGInternalId(
                update.agMemberId, groupFrame.anonymousGroupInternalId
            ) ?: return@withContext null
            val texts = circleEventTexts(
                memberId = member.id,
                memberName = member.name,
                transitions = update.transitions,
                circles = listCircles(group.internalId).associateBy { it.id },
                mutedTargets = listMutedTargets(group.internalId)
            )
            if (texts.isEmpty()) null else CircleEvents(group.id, group.name, member.id, texts)
        }

    suspend fun listMutedTargets(anonymousGroupInternalId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.listMutedTargets(anonymousGroupInternalId).toSet()
        }

    suspend fun setTargetMuted(anonymousGroupInternalId: Long, targetId: String, muted: Boolean) =
        withContext(Dispatchers.IO) {
            if (muted) {
                anonymousGroupDao.insertMutedTarget(
                    MutedTargetEntity(anonymousGroupInternalId, targetId)
                )
            } else {
                anonymousGroupDao.deleteMutedTarget(anonymousGroupInternalId, targetId)
            }
        }

    suspend fun deleteAnonymousGroup(connectionSettingsId: Long, anonymousGroupInternalId: Long) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            val response = httpClient.delete {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                    )
                }
            }
            when (response.status.value) {
                200 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
            deleteLocalAnonymousGroup(anonymousGroup)
        }

    suspend fun promoteMember(
        connectionSettingsId: Long, anonymousGroupInternalId: Long, agMemberId: String
    ) = withContext(Dispatchers.IO) {
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val response = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/$agMemberId/promote")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(
                    HttpHeaders.Authorization,
                    "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                )
            }
        }
        try {
            when (response.status.value) {
                200 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
        } catch (e: AGMemberNotAdminException) {
            anonymousGroupDao.setAGMemberIsAGAdminSelf(
                anonymousGroupInternalId = anonymousGroupInternalId, memberIsAGAdmin = false
            )
            throw e
        }
        anonymousGroupDao.setAGMemberIsAGAdmin(
            id = agMemberId, agInternalId = anonymousGroupInternalId, isAGAdmin = true
        )
    }
}
