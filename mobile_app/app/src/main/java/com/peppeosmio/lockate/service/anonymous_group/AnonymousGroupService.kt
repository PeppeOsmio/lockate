package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGMemberMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.CircleMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.LocationRecordMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGFrameDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberMessageReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.CircleUpsertReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.data.anonymous_group.remote.MemberLocationDto
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.ChannelState
import com.peppeosmio.lockate.domain.anonymous_group.ChannelStatus
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.GroupFrame
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
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
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.geofence.CircleEventQueue
import com.peppeosmio.lockate.service.geofence.CircleEvents
import com.peppeosmio.lockate.service.geofence.GeofenceTracker
import com.peppeosmio.lockate.service.geofence.circleEventTexts
import com.peppeosmio.lockate.service.location.PositionProvider
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.IOException
import java.net.ConnectException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.time.inMs

internal enum class StreamRetryAction {
    Stop,
    AwaitNetwork,
    Backoff,
}

/**
 * Decides what the group channel does after it ended: a missing network parks on the connectivity
 * event, everything else but errors that retrying can't fix uses a timed backoff.
 */
internal fun classifyStreamFailure(e: Throwable?, isNetworkAvailable: Boolean): StreamRetryAction =
    when {
        e is UnauthorizedException ||
            e is RemoteAGNotFoundException ||
            e is InvalidApiKeyException -> StreamRetryAction.Stop
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
    private val sendLoopLog: SendLoopLog,
) {
    // tryEmit drops what doesn't fit, and losing a NewAnonymousGroupEvent or a
    // DeleteAnonymousGroupEvent leaves the foreground service holding the wrong groups
    private val _events =
        MutableSharedFlow<AnonymousGroupEvent>(extraBufferCapacity = Channel.UNLIMITED)
    val events = _events.asSharedFlow()

    suspend fun listLocalAnonymousGroups(connectionSettingsId: Long): List<AnonymousGroup> =
        withContext(Dispatchers.IO) {
            val entities = anonymousGroupDao.listAnonymousGroupsOfConnection(connectionSettingsId)
            entities.map {
                AnonymousGroupMapper.toDomain(
                    entity = it,
                    keyStoreService = keyStoreService,
                )
            }
        }

    suspend fun getAGByInternalId(anonymousGroupInternalId: Long): AnonymousGroup =
        withContext(Dispatchers.IO) {
            val entity =
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                    ?: throw LocalAGNotFoundException()
            AnonymousGroupMapper.toDomain(
                entity = entity,
                keyStoreService = keyStoreService,
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
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }

    suspend fun setAGSendLocation(anonymousGroup: AnonymousGroup, sendLocation: Boolean) {
        anonymousGroupDao.setAGSendLocation(
            anonymousGroupInternalId = anonymousGroup.internalId,
            sendLocation = sendLocation,
        )
        if (sendLocation) {
            _events.tryEmit(
                AnonymousGroupEvent.SendEnabledEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        } else {
            _events.tryEmit(
                AnonymousGroupEvent.SendDisabledEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }
    }

    private suspend fun setAGIsMemberFalse(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            Log.d("setAGIsMemberFalse", "Setting false")
            anonymousGroupDao.setAGIsMember(
                anonymousGroupInternalId = anonymousGroup.internalId,
                isMember = false,
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
                anonymousGroupInternalId = anonymousGroup.internalId,
                isMember = true,
            )
            _events.tryEmit(
                AnonymousGroupEvent.ReaddedToAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }

    private suspend fun setAGExistsRemoteFalse(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGExistsRemote(
                anonymousGroupInternalId = anonymousGroup.internalId,
                existsRemote = false,
            )
            _events.tryEmit(
                AnonymousGroupEvent.RemoteAGDoesntExistEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }

    private suspend fun setAGExistsRemoteTrue(anonymousGroup: AnonymousGroup) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGExistsRemote(
                anonymousGroupInternalId = anonymousGroup.internalId,
                existsRemote = true,
            )
            _events.tryEmit(
                AnonymousGroupEvent.RemoteAGExistsEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                )
            )
        }

    @Throws(
        UnauthorizedException::class,
        InvalidApiKeyException::class,
        RemoteAGNotFoundException::class,
        APIException::class,
    )
    suspend fun leaveAnonymousGroup(anonymousGroupInternalId: Long, connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {
            val agEntity =
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                    ?: throw LocalAGNotFoundException()
            val anonymousGroup =
                AnonymousGroupMapper.toDomain(
                    entity = agEntity,
                    keyStoreService = keyStoreService,
                )

            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val response = httpClient.post {
                url(
                    "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/leave"
                )
                headers {
                    connectionSettings.apiKey?.let { ak -> append(name = "X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                    )
                    append(HttpHeaders.ContentType, "application/json")
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401,
                    403 -> {
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
        ConnectException::class,
    )
    suspend fun createAnonymousGroup(
        connectionId: Long,
        groupName: String,
        memberName: String,
    ): AnonymousGroup =
        withContext(Dispatchers.IO) {
            val key = cryptoService.generateGroupKey()
            val groupId = UUID.randomUUID().toString()
            val memberId = UUID.randomUUID().toString()
            val encryptedMemberNameDto =
                EncryptedDataMapper.toDto(
                    payloadCodec.encryptMemberName(memberName, key, groupId, memberId)
                )
            val connectionSettings = connectionService.getConnectionSettingsById(connectionId)
            val encryptedGroupNameDto =
                EncryptedDataMapper.toDto(payloadCodec.encryptGroupName(groupName, key, groupId))

            val createResponse = httpClient.post {
                url("${connectionSettings.url}/api/anonymous-groups")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(
                    AGCreateReqDto(
                        groupId = groupId,
                        memberId = memberId,
                        encryptedMemberName = encryptedMemberNameDto,
                        encryptedGroupName = encryptedGroupNameDto,
                    )
                )
            }

            when (createResponse.status.value) {
                201 -> Unit
                401,
                403 -> ErrorHandler.handleUnauthorized(createResponse)
                else -> ErrorHandler.handleGeneric(createResponse)
            }

            val createResponseBody = createResponse.body<AGCreateResDto>()
            val agMember =
                AGMemberMapper.dtoToDomain(
                    encryptedAGMemberDto = createResponseBody.authenticatedMemberInfo.member,
                    payloadCodec = payloadCodec,
                    key = key,
                    groupId = createResponseBody.anonymousGroup.id,
                )
            val agMemberEntity =
                AGMemberMapper.domainToEntity(
                    agMember = agMember,
                    anonymousGroupInternalId = 0,
                )
            val anonymousGroup =
                AnonymousGroup(
                    internalId = 0,
                    id = createResponseBody.anonymousGroup.id,
                    name = groupName,
                    createdAt = createResponseBody.anonymousGroup.createdAt,
                    joinedAt = createResponseBody.authenticatedMemberInfo.member.createdAt,
                    memberName = memberName,
                    memberId = agMemberEntity.id,
                    memberToken = Base64.decode(createResponseBody.authenticatedMemberInfo.token),
                    memberIsAGAdmin =
                        createResponseBody.authenticatedMemberInfo.member
                            .isAGAdmin, // should be true
                    isMember = true,
                    existsRemote = true,
                    sendLocation = true,
                    key = key,
                    connectionId = connectionId,
                )
            val anonymousGroupInternalId =
                anonymousGroupDao.createAGWithMembers(
                    agEntity =
                        AnonymousGroupMapper.toEntity(
                            anonymousGroup = anonymousGroup,
                            keyStoreService = keyStoreService,
                        ),
                    agMemberEntities = listOf(agMemberEntity),
                )
            _events.tryEmit(
                AnonymousGroupEvent.NewAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroupInternalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
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
                memberIsAGAdmin =
                    createResponseBody.authenticatedMemberInfo.member.isAGAdmin, // should be true
                isMember = true,
                existsRemote = true,
                sendLocation = true,
                key = key,
                connectionId = connectionId,
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
        ConnectException::class,
    )
    suspend fun joinWithInvite(
        connectionId: Long,
        anonymousGroupId: String,
        groupKey: ByteArray,
        joinToken: ByteArray,
        memberName: String,
    ): AnonymousGroup =
        withContext(Dispatchers.IO) {
            if (
                anonymousGroupDao.getAGByIdAndConnectionId(
                    anonymousGroupId = anonymousGroupId,
                    connectionId = connectionId,
                ) != null
            ) {
                throw LocalAGExistsException()
            }
            val connectionSettings = connectionService.getConnectionSettingsById(connectionId)
            val memberId = UUID.randomUUID().toString()
            val encryptedMemberName =
                payloadCodec.encryptMemberName(memberName, groupKey, anonymousGroupId, memberId)
            val response = httpClient.post {
                url(
                    "${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/join"
                )
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(
                    AGJoinReqDto(
                        joinToken = Base64.encode(joinToken),
                        memberId = memberId,
                        encryptedMemberName = EncryptedDataMapper.toDto(encryptedMemberName),
                    )
                )
            }
            when (response.status.value) {
                200 -> Unit
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
            val body = response.body<AGJoinResDto>()
            val groupName =
                payloadCodec.decryptGroupName(
                    EncryptedDataMapper.toDomain(body.anonymousGroup.encryptedName),
                    groupKey,
                    body.anonymousGroup.id,
                )
            val agMember =
                AGMemberMapper.dtoToDomain(
                    encryptedAGMemberDto = body.authenticatedMemberInfo.member,
                    payloadCodec = payloadCodec,
                    key = groupKey,
                    groupId = body.anonymousGroup.id,
                )
            val agMemberEntity =
                AGMemberMapper.domainToEntity(
                    agMember = agMember,
                    anonymousGroupInternalId = 0,
                )
            // the members already in the group, so we see them before the socket's snapshot
            val otherMembers =
                body.members
                    .filter { it.id != agMember.id }
                    .mapDecryptable(
                        onUndecryptable = {
                            Log.e(
                                "",
                                "Skipped member ${it.id} of ${body.anonymousGroup.id}: can't decrypt it",
                            )
                        }
                    ) {
                        AGMemberMapper.dtoToDomain(
                            it,
                            payloadCodec,
                            groupKey,
                            body.anonymousGroup.id,
                        )
                    }
            val anonymousGroup =
                AnonymousGroup(
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
                    connectionId = connectionId,
                )
            val anonymousGroupInternalId =
                anonymousGroupDao.createAGWithMembers(
                    agEntity =
                        AnonymousGroupMapper.toEntity(
                            anonymousGroup = anonymousGroup,
                            keyStoreService = keyStoreService,
                        ),
                    agMemberEntities =
                        listOf(agMemberEntity) +
                            otherMembers.map {
                                AGMemberMapper.domainToEntity(it, anonymousGroupInternalId = 0)
                            },
                )
            _events.tryEmit(
                AnonymousGroupEvent.NewAnonymousGroupEvent(
                    anonymousGroupInternalId = anonymousGroupInternalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = connectionId,
                )
            )
            anonymousGroup.copy(internalId = anonymousGroupInternalId)
        }

    suspend fun verifyMemberAuth(connectionSettingsId: Long, anonymousGroupInternalId: Long) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val agEntity =
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                    ?: throw LocalAGNotFoundException()
            val anonymousGroup =
                AnonymousGroupMapper.toDomain(
                    entity = agEntity,
                    keyStoreService = keyStoreService,
                )
            val response = httpClient.get {
                url(
                    "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/auth/verify"
                )
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                    )
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401,
                    403 -> ErrorHandler.handleUnauthorized(response)
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
                connectionSettingsId = connectionSettingsId,
            )
        }
    }

    suspend fun deleteAllAG(connectionSettingsId: Long) {
        listLocalAnonymousGroups(connectionSettingsId).forEach { deleteLocalAnonymousGroup(it) }
    }

    private suspend fun getAvailableConnections(
        connectionList: List<Connection>
    ): List<Connection> = coroutineScope {
        connectionList
            .map { settings ->
                async {
                    try {
                        connectionService.isApiAvailable(settings.url)
                        settings
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
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

    private val groupLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * The socket's frames are applied and emitted under it, so a replayed snapshot can't overtake
     * one of them.
     */
    private fun groupLock(anonymousGroupId: String) =
        groupLocks.getOrPut(anonymousGroupId) { Mutex() }

    /**
     * Keeps the group's websocket open for as long as the caller is suspended here, sharing it with
     * whoever else holds the group: the foreground service holds every group, the open map holds
     * the one it shows. The location is sent while at least one holder asks for it with [send].
     * [onStatus] gets the state of the connection and of the location.
     */
    suspend fun holdChannel(
        anonymousGroup: AnonymousGroup,
        send: Boolean,
        onStatus: (ChannelStatus) -> Unit = {},
    ): Nothing {
        var isOpen = true
        val channel = channelsMutex.withLock {
            // A channel that stopped for good (removed, group gone) stays until its holders let go:
            // a new holder gets a fresh one, which finds out again whether the group is back
            val channel =
                channels[anonymousGroup.id]?.takeIf { it.job.isActive }
                    ?: GroupChannel().also { c ->
                        isOpen = false
                        c.job = channelScope.launch { runChannel(anonymousGroup, c) }
                        channels[anonymousGroup.id] = c
                    }
            channel.leases++
            if (send) channel.sendLeases.update { it + 1 }
            channel
        }
        try {
            // the server's snapshot went only to the holders the channel had then
            if (isOpen) replaySnapshot(anonymousGroup)
            channel.status.collect { onStatus(it) }
        } finally {
            withContext(NonCancellable) {
                channelsMutex.withLock {
                    if (send) channel.sendLeases.update { it - 1 }
                    if (--channel.leases == 0) {
                        channel.job.cancel()
                        // a stopped channel may have been replaced meanwhile
                        channels.remove(anonymousGroup.id, channel)
                    }
                }
            }
        }
    }

    /** The frames are applied to the database before they are emitted, so it holds the snapshot. */
    private suspend fun replaySnapshot(anonymousGroup: AnonymousGroup) =
        groupLock(anonymousGroup.id).withLock {
            val frame =
                AGChannelFrame.Snapshot(
                    members = getLocalAGMembers(anonymousGroup),
                    circles = listCircles(anonymousGroup.internalId),
                )
            _groupFrames.emit(GroupFrame(anonymousGroup.internalId, anonymousGroup.id, frame))
        }

    /**
     * Connects the group's websocket, reconnecting when it drops. The location is followed while
     * the group has a send lease, also while the socket is down, so the circle events found
     * meanwhile wait in a queue and go out when it is back.
     */
    private suspend fun runChannel(anonymousGroup: AnonymousGroup, channel: GroupChannel) =
        coroutineScope {
            // the saved circles, so we geofence also when the server can't be reached
            val tracker =
                GeofenceTracker().apply {
                    replaceCircles(listCircles(anonymousGroup.internalId))
                }
            val session = MutableStateFlow<DefaultClientWebSocketSession?>(null)
            val circleEvents = CircleEventQueue()
            val sender = launch {
                channel.sendLeases
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .collectLatest { sending ->
                        if (sending) {
                            sendLocationWhileEnabled(
                                anonymousGroup,
                                channel,
                                tracker,
                                session,
                                circleEvents,
                            )
                        }
                    }
            }
            connectChannel(anonymousGroup, channel, tracker, session, circleEvents)
            sender.cancel()
        }

    private suspend fun responseFailure(response: HttpResponse, e: Exception): Exception =
        try {
            when (response.status.value) {
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
            e
        } catch (ee: Exception) {
            ee
        }

    @OptIn(ExperimentalTime::class)
    private suspend fun connectChannel(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        tracker: GeofenceTracker,
        session: MutableStateFlow<DefaultClientWebSocketSession?>,
        circleEvents: CircleEventQueue,
    ) {
        val connectionSettings =
            connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        val maxRetries = 10
        var retries = 0
        val tag = "channel ${anonymousGroup.id}"
        while (true) {
            var isConnected = false
            try {
                sendLoopLog.log("$tag connecting")
                var removed = false
                httpClient.webSocket(
                    request = {
                        url(
                            "${connectionSettings.getWebSocketUrl()}/api/ws/anonymous-groups/${anonymousGroup.id}"
                        )
                        headers {
                            connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                            append(
                                HttpHeaders.Authorization,
                                "AGMember ${anonymousGroup.memberId} ${
                                Base64.encode(anonymousGroup.memberToken)
                            }",
                            )
                        }
                    }
                ) {
                    isConnected = true
                    retries = 0
                    channel.status.update { it.copy(state = ChannelState.Connected) }
                    sendLoopLog.log("$tag connected")
                    session.value = this
                    try {
                        sendCircleEvents(anonymousGroup, this, circleEvents)
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val dto = Json.decodeFromString<AGFrameDto>(frame.readText())
                            groupLock(anonymousGroup.id).withLock {
                                handleFrame(
                                    anonymousGroup = anonymousGroup,
                                    dto = dto,
                                    tracker = tracker,
                                )
                            }
                        }
                    } finally {
                        session.value = null
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
                val failure = if (e is ResponseException) responseFailure(e.response, e) else e
                when (failure) {
                    is UnauthorizedException -> setAGIsMemberFalse(anonymousGroup)
                    is RemoteAGNotFoundException -> setAGExistsRemoteFalse(anonymousGroup)
                }
                val action =
                    classifyStreamFailure(failure, connectivityService.isNetworkAvailable())
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
                        withTimeoutOrNull(60.seconds) {
                            connectivityService.awaitNetworkAvailable()
                        }
                    }

                    else -> {
                        retries = min(retries + 1, maxRetries)
                        val waitSeconds = nextBackoffSeconds(retries)
                        Log.e(
                            "",
                            "Can't connect to ${connectionSettings.url} retrying in $waitSeconds s",
                        )
                        delay(waitSeconds.seconds)
                    }
                }
                sendLoopLog.log("$tag wait over")
            }
        }
    }

    /**
     * Follows the location and sends it while the channel is open. GPS faults don't close the
     * channel, which keeps receiving: they are reported in the channel's status and the sending
     * resumes by itself.
     */
    private suspend fun sendLocationWhileEnabled(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        tracker: GeofenceTracker,
        session: StateFlow<DefaultClientWebSocketSession?>,
        circleEvents: CircleEventQueue,
    ) {
        // we may have moved anywhere since the last time we shared the location
        tracker.forgetPosition()
        try {
            while (true) {
                try {
                    locationService.checkPermissionsAndLocationEnabled()
                    channel.status.update { it.copy(isLocationDisabled = false) }
                    sendLocation(anonymousGroup, channel, tracker, session, circleEvents)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    when (e) {
                        is LocationDisabledException,
                        is NoPermissionException -> {
                            channel.status.update { it.copy(isLocationDisabled = true) }
                            // the timeout is a safety net so we re-check even if the signal never
                            // arrives
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

    /**
     * False when the socket died under us: Ktor then throws, a CancellationException too, though
     * nobody cancelled us. Taken as a real cancellation it would stop the sending for good while
     * the channel reconnects.
     */
    private suspend fun trySend(
        session: DefaultClientWebSocketSession,
        message: AGMemberMessageReqDto,
    ): Boolean =
        try {
            session.send(Frame.Text(Json.encodeToString(message)))
            true
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            false
        }

    /** False when the socket died: the events that didn't go out wait for the next one. */
    private suspend fun sendCircleEvents(
        anonymousGroup: AnonymousGroup,
        session: DefaultClientWebSocketSession,
        circleEvents: CircleEventQueue,
    ): Boolean {
        val events = circleEvents.drain(System.currentTimeMillis())
        events.forEachIndexed { i, event ->
            val encrypted =
                payloadCodec.encryptCircleEvent(
                    event.transition,
                    anonymousGroup.key,
                    anonymousGroup.id,
                    anonymousGroup.memberId,
                )
            val message = AGMemberMessageReqDto.CircleEvent(EncryptedDataMapper.toDto(encrypted))
            if (!trySend(session, message)) {
                circleEvents.putBack(events.drop(i))
                return false
            }
        }
        return true
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun sendLocation(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        tracker: GeofenceTracker,
        session: StateFlow<DefaultClientWebSocketSession?>,
        circleEvents: CircleEventQueue,
    ) = coroutineScope {
        Log.i(
            "",
            "Sending AG location agId: ${anonymousGroup.id} agMemberId: ${anonymousGroup.memberId}",
        )
        suspend fun getTimeoutJob() {
            delay(30000L)
            Log.e("", "No location obtained in the last 30 s")
            throw LocationTimeoutException()
        }

        var lastSent: Instant? = null
        var lastMode: Pair<UserActivity, Boolean>? = null

        var timeoutJob: Job? = launch {
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
                sendLoopLog.log(
                    "channel ${anonymousGroup.id} activity=${mode.first} fresh=${mode.second}"
                )
            }
            channel.status.update { it.copy(isLocationUnavailable = false) }

            timeoutJob?.cancel()
            // while still the position comes once a minute
            timeoutJob =
                if (position.activity == UserActivity.Still) null
                else
                    launch {
                        getTimeoutJob()
                    }
            tracker.update(position.fix).forEach {
                circleEvents.add(it, now.toEpochMilliseconds())
            }
            val current = session.value ?: return@collect
            if (!sendCircleEvents(anonymousGroup, current, circleEvents)) return@collect

            lastSent?.let {
                if ((now - it).inMs < 1500) {
                    return@collect
                }
            }

            val encrypted =
                payloadCodec.encryptMemberMessage(
                    message =
                        MemberMessage(coordinates = coordinates, activity = position.activity),
                    key = anonymousGroup.key,
                    groupId = anonymousGroup.id,
                    memberId = anonymousGroup.memberId,
                )
            val sent =
                trySend(
                    current,
                    AGMemberMessageReqDto.Location(
                        encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                        encryptedActivity = EncryptedDataMapper.toDto(encrypted.activity),
                    ),
                )
            // the channel reconnects by itself, the next position goes out then
            if (!sent) return@collect
            lastSent = now
            _events.tryEmit(
                AnonymousGroupEvent.AGLocationSentEvent(
                    anonymousGroupInternalId = anonymousGroup.internalId,
                    anonymousGroupId = anonymousGroup.id,
                    connectionId = anonymousGroup.connectionId,
                    locationRecord =
                        LocationRecord(
                            coordinates = coordinates,
                            timestamp = Clock.System.now().toLocalDateTime(TimeZone.UTC),
                            activity = position.activity,
                        ),
                )
            )
        }
    }

    private suspend fun decryptMember(
        anonymousGroup: AnonymousGroup,
        dto: EncryptedAGMemberDto,
    ): AGMember =
        AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = dto,
            payloadCodec = payloadCodec,
            key = anonymousGroup.key,
            groupId = anonymousGroup.id,
        )

    private suspend fun handleFrame(
        anonymousGroup: AnonymousGroup,
        dto: AGFrameDto,
        tracker: GeofenceTracker,
    ) {
        val internalId = anonymousGroup.internalId
        val frame: AGChannelFrame =
            try {
                when (dto.type) {
                    AGFrameDto.SNAPSHOT -> {
                        // the socket carries no positions: keep those the locations stream saved
                        val knownLocations =
                            anonymousGroupDao.listAGMembers(internalId).associate {
                                it.id to AGMemberMapper.entityToDomain(it).lastLocationRecord
                            }
                        val members =
                            dto.members!!
                                .mapDecryptable(
                                    onUndecryptable = {
                                        Log.e(
                                            "",
                                            "Skipped member ${it.id} of ${anonymousGroup.id}: can't decrypt it",
                                        )
                                    }
                                ) {
                                    decryptMember(anonymousGroup, it)
                                }
                                .map {
                                    it.copy(
                                        lastLocationRecord =
                                            it.lastLocationRecord ?: knownLocations[it.id]
                                    )
                                }
                        val circles =
                            dto.circles!!.mapDecryptable(
                                onUndecryptable = {
                                    Log.e(
                                        "",
                                        "Skipped circle ${it.id} of ${anonymousGroup.id}: can't decrypt it",
                                    )
                                }
                            ) {
                                CircleMapper.dtoToDomain(
                                    it,
                                    payloadCodec,
                                    anonymousGroup.key,
                                    anonymousGroup.id,
                                )
                            }
                        members
                            .firstOrNull { it.id == anonymousGroup.memberId }
                            ?.let { me ->
                                if (me.isAGAdmin != anonymousGroup.memberIsAGAdmin) {
                                    anonymousGroupDao.setAGMemberIsAGAdminSelf(
                                        internalId,
                                        me.isAGAdmin,
                                    )
                                }
                            }
                        anonymousGroupDao.setAGMembers(
                            anonymousGroupInternalId = internalId,
                            agMemberEntities =
                                members.map {
                                    AGMemberMapper.domainToEntity(
                                        it,
                                        anonymousGroupInternalId = internalId,
                                    )
                                },
                        )
                        anonymousGroupDao.syncCircles(internalId, circles)
                        tracker.replaceCircles(circles)
                        AGChannelFrame.Snapshot(members, circles)
                    }

                    AGFrameDto.MEMBER_JOINED -> {
                        val member = decryptMember(anonymousGroup, dto.member!!)
                        if (
                            anonymousGroupDao.getAGMemberByIdAndAGInternalId(
                                member.id,
                                internalId,
                            ) == null
                        ) {
                            anonymousGroupDao.insertAGMember(
                                AGMemberMapper.domainToEntity(
                                    member,
                                    anonymousGroupInternalId = internalId,
                                )
                            )
                        }
                        AGChannelFrame.MemberJoined(member)
                    }

                    AGFrameDto.MEMBER_LEFT -> {
                        anonymousGroupDao.deleteAGMemberById(dto.memberId!!, internalId)
                        AGChannelFrame.MemberLeft(dto.memberId)
                    }

                    AGFrameDto.MEMBER_UPDATED -> {
                        anonymousGroupDao.setAGMemberIsAGAdmin(
                            dto.memberId!!,
                            internalId,
                            dto.isAGAdmin!!,
                        )
                        if (dto.memberId == anonymousGroup.memberId) {
                            anonymousGroupDao.setAGMemberIsAGAdminSelf(internalId, dto.isAGAdmin)
                        }
                        AGChannelFrame.MemberUpdated(dto.memberId, dto.isAGAdmin)
                    }

                    AGFrameDto.CIRCLE_EVENT -> {
                        val memberId = dto.memberId!!
                        // the server never relays our own events, but if it did they mustn't tell
                        // us
                        // about ourselves
                        if (memberId == anonymousGroup.memberId) return
                        val circleEvent = dto.circleEvent!!
                        AGChannelFrame.CircleEvent(
                            memberId = memberId,
                            transition =
                                payloadCodec.decryptCircleEvent(
                                    EncryptedDataMapper.toDomain(circleEvent.encryptedEvent),
                                    anonymousGroup.key,
                                    anonymousGroup.id,
                                    memberId,
                                ),
                            timestamp = circleEvent.timestamp,
                        )
                    }

                    AGFrameDto.CIRCLE_UPSERTED -> {
                        val circle =
                            CircleMapper.dtoToDomain(
                                dto.circle!!,
                                payloadCodec,
                                anonymousGroup.key,
                                anonymousGroup.id,
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
     * Streams the positions of the group's members for as long as the caller is suspended here:
     * first the last saved one of each, then the live ones. Only the open map holds it, so the
     * phone receives the others' positions only while someone looks at them. The positions are
     * saved locally before [onLocations] gets them.
     */
    suspend fun holdLocationStream(
        anonymousGroup: AnonymousGroup,
        onState: (ChannelState) -> Unit,
        onLocations: (List<AGLocationUpdate>) -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
            val maxRetries = 10
            var retries = 0
            while (true) {
                try {
                    httpClient.sse(
                        request = {
                            url(
                                "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/locations"
                            )
                            headers {
                                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                                append(
                                    HttpHeaders.Authorization,
                                    "AGMember ${anonymousGroup.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                                )
                            }
                        }
                    ) {
                        retries = 0
                        onState(ChannelState.Connected)
                        incoming.collect { event ->
                            val locations =
                                when (event.event) {
                                    "snapshot" ->
                                        Json.decodeFromString<List<MemberLocationDto>>(event.data!!)
                                    "location" ->
                                        listOf(
                                            Json.decodeFromString<MemberLocationDto>(event.data!!)
                                        )
                                    else -> return@collect
                                }
                            onLocations(saveLocations(anonymousGroup, locations))
                        }
                    }
                    throw IOException("Locations stream closed by the server")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val response = (e as? SSEClientException)?.response
                    val failure = response?.let { responseFailure(it, e) } ?: e
                    when (
                        classifyStreamFailure(failure, connectivityService.isNetworkAvailable())
                    ) {
                        // the group's channel finds out too, and marks the group
                        StreamRetryAction.Stop -> {
                            onState(ChannelState.Stopped)
                            awaitCancellation()
                        }

                        StreamRetryAction.AwaitNetwork -> {
                            onState(ChannelState.Reconnecting)
                            withTimeoutOrNull(60.seconds) {
                                connectivityService.awaitNetworkAvailable()
                            }
                        }

                        StreamRetryAction.Backoff -> {
                            onState(ChannelState.Reconnecting)
                            retries = min(retries + 1, maxRetries)
                            delay(nextBackoffSeconds(retries).seconds)
                        }
                    }
                }
            }
        }

    @OptIn(ExperimentalTime::class)
    private suspend fun saveLocations(
        anonymousGroup: AnonymousGroup,
        locations: List<MemberLocationDto>,
    ): List<AGLocationUpdate> =
        locations.mapDecryptable(
            onUndecryptable = {
                Log.e(
                    "",
                    "Skipped a location of ${it.memberId} in ${anonymousGroup.id}: can't decrypt it",
                )
            }
        ) { dto ->
            val record =
                LocationRecordMapper.toDomain(
                    encryptedLocationRecordDto = dto.record,
                    payloadCodec = payloadCodec,
                    key = anonymousGroup.key,
                    groupId = anonymousGroup.id,
                    memberId = dto.memberId,
                )
            val lastSeen = record.timestamp.toInstant(TimeZone.UTC).toEpochMilliseconds()
            // a member unknown locally joined after our last snapshot: its joined frame brings it
            // in
            anonymousGroupDao
                .getAGMemberByIdAndAGInternalId(dto.memberId, anonymousGroup.internalId)
                ?.takeIf { it.lastSeen == null || it.lastSeen < lastSeen }
                ?.let { member ->
                    anonymousGroupDao.setAGMemberLastLocation(
                        agMemberInternalId = member.internalId,
                        lastLatitude = record.coordinates.latitude,
                        lastLongitude = record.coordinates.longitude,
                        lastSeen = lastSeen,
                        lastActivity = record.activity.name,
                    )
                }
            AGLocationUpdate(record, dto.memberId)
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
                        isLocationUnavailable = sending.any { it.second.isLocationUnavailable },
                    )
                )
            }

            suspend fun start(entity: AnonymousGroupEntity) {
                val anonymousGroup =
                    AnonymousGroupMapper.toDomain(
                        entity = entity,
                        keyStoreService = keyStoreService,
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

            // Listed once subscribed, so a group created, left or toggled meanwhile isn't missed;
            // an
            // event about a group the listing already handled just starts or stops it again
            events
                .onSubscription {
                    anonymousGroupDao.listAGsToHold().forEach { start(it) }
                    emitStatus()
                }
                .collect { event ->
                    when (event) {
                        is AnonymousGroupEvent.NewAnonymousGroupEvent ->
                            startByInternalId(event.anonymousGroupInternalId)
                        is AnonymousGroupEvent.ReaddedToAnonymousGroupEvent ->
                            startByInternalId(event.anonymousGroupInternalId)
                        is AnonymousGroupEvent.RemoteAGExistsEvent ->
                            startByInternalId(event.anonymousGroupInternalId)
                        is AnonymousGroupEvent.SendEnabledEvent ->
                            startByInternalId(event.anonymousGroupInternalId)
                        is AnonymousGroupEvent.SendDisabledEvent ->
                            startByInternalId(event.anonymousGroupInternalId)
                        is AnonymousGroupEvent.DeleteAnonymousGroupEvent ->
                            stop(event.anonymousGroupId)
                        is AnonymousGroupEvent.RemovedFromAnonymousGroupEvent ->
                            stop(event.anonymousGroupId)
                        is AnonymousGroupEvent.RemoteAGDoesntExistEvent ->
                            stop(event.anonymousGroupId)
                        else -> Unit
                    }
                }
        }

    private suspend fun circleRequest(
        connectionSettingsId: Long,
        anonymousGroupInternalId: Long,
        circleId: String,
        method: HttpMethod,
        body: CircleUpsertReqDto?,
    ) {
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val agEntity =
            anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                ?: throw LocalAGNotFoundException()
        val anonymousGroup =
            AnonymousGroupMapper.toDomain(
                entity = agEntity,
                keyStoreService = keyStoreService,
            )
        val response = httpClient.request {
            this.method = method
            url(
                "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/circles/$circleId"
            )
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(
                    HttpHeaders.Authorization,
                    "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
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
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
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
                circle = circle,
            )
        )
    }

    suspend fun listCircles(anonymousGroupInternalId: Long): List<Circle> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.listCircles(anonymousGroupInternalId).map {
                CircleMapper.entityToDomain(it)
            }
        }

    suspend fun upsertCircle(
        connectionSettingsId: Long,
        anonymousGroup: AnonymousGroup,
        circle: Circle,
    ) =
        withContext(Dispatchers.IO) {
            val encrypted =
                payloadCodec.encryptCircle(circle, anonymousGroup.key, anonymousGroup.id)
            circleRequest(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupInternalId = anonymousGroup.internalId,
                circleId = circle.id,
                method = HttpMethod.Put,
                body =
                    CircleUpsertReqDto(
                        encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                        encryptedRadius = EncryptedDataMapper.toDto(encrypted.radius),
                        encryptedName = EncryptedDataMapper.toDto(encrypted.name),
                    ),
            )
            anonymousGroupDao.upsertCircle(anonymousGroup.internalId, circle)
            _groupFrames.emit(
                GroupFrame(
                    anonymousGroup.internalId,
                    anonymousGroup.id,
                    AGChannelFrame.CircleUpserted(circle),
                )
            )
        }

    suspend fun deleteCircle(
        connectionSettingsId: Long,
        anonymousGroup: AnonymousGroup,
        circleId: String,
    ) =
        withContext(Dispatchers.IO) {
            circleRequest(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupInternalId = anonymousGroup.internalId,
                circleId = circleId,
                method = HttpMethod.Delete,
                body = null,
            )
            anonymousGroupDao.deleteCircle(circleId, anonymousGroup.internalId)
            _groupFrames.emit(
                GroupFrame(
                    anonymousGroup.internalId,
                    anonymousGroup.id,
                    AGChannelFrame.CircleDeleted(circleId),
                )
            )
        }

    /** The event to notify for a live frame, with the group and the member. */
    suspend fun describeCircleEvents(groupFrame: GroupFrame): CircleEvents? =
        withContext(Dispatchers.IO) {
            val event = groupFrame.frame as? AGChannelFrame.CircleEvent ?: return@withContext null
            val group =
                anonymousGroupDao.getAGByInternalId(groupFrame.anonymousGroupInternalId)
                    ?: return@withContext null
            val member =
                anonymousGroupDao.getAGMemberByIdAndAGInternalId(
                    event.memberId,
                    groupFrame.anonymousGroupInternalId,
                ) ?: return@withContext null
            val texts =
                circleEventTexts(
                    memberId = member.id,
                    memberName = member.name,
                    transitions = listOf(event.transition),
                    circles = listCircles(group.internalId).associateBy { it.id },
                    mutedTargets = listMutedTargets(group.internalId),
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
            val agEntity =
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                    ?: throw LocalAGNotFoundException()
            val anonymousGroup =
                AnonymousGroupMapper.toDomain(
                    entity = agEntity,
                    keyStoreService = keyStoreService,
                )
            val response = httpClient.delete {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                    )
                }
            }
            when (response.status.value) {
                200 -> Unit
                401,
                403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
            deleteLocalAnonymousGroup(anonymousGroup)
        }

    suspend fun promoteMember(
        connectionSettingsId: Long,
        anonymousGroupInternalId: Long,
        agMemberId: String,
    ) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val agEntity =
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                    ?: throw LocalAGNotFoundException()
            val anonymousGroup =
                AnonymousGroupMapper.toDomain(
                    entity = agEntity,
                    keyStoreService = keyStoreService,
                )
            val response = httpClient.post {
                url(
                    "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members/$agMemberId/promote"
                )
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                    )
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401,
                    403 -> ErrorHandler.handleUnauthorized(response)
                    404 -> throw RemoteAGNotFoundException()
                    else -> ErrorHandler.handleGeneric(response)
                }
            } catch (e: AGMemberNotAdminException) {
                anonymousGroupDao.setAGMemberIsAGAdminSelf(
                    anonymousGroupInternalId = anonymousGroupInternalId,
                    memberIsAGAdmin = false,
                )
                throw e
            }
            anonymousGroupDao.setAGMemberIsAGAdmin(
                id = agMemberId,
                agInternalId = anonymousGroupInternalId,
                isAGAdmin = true,
            )
        }
}
