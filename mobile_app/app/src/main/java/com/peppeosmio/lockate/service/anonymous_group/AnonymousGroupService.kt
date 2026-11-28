package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGMemberMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.LocationRecordMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.PlaceMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGFrameDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGJoinResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGPostLocationReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGPostLocationResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedAGMemberDto
import com.peppeosmio.lockate.data.anonymous_group.remote.MemberLocationDto
import com.peppeosmio.lockate.data.anonymous_group.remote.PlaceDto
import com.peppeosmio.lockate.data.anonymous_group.remote.PlaceUpsertReqDto
import com.peppeosmio.lockate.data.apiJson
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.ChannelState
import com.peppeosmio.lockate.domain.anonymous_group.ChannelStatus
import com.peppeosmio.lockate.domain.anonymous_group.GroupFrame
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.domain.anonymous_group.PlaceTransition
import com.peppeosmio.lockate.domain.anonymous_group.SendLocationStatus
import com.peppeosmio.lockate.domain.crypto.EncryptedData
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
import com.peppeosmio.lockate.platform_service.WakeLockService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.geofence.GeofenceTracker
import com.peppeosmio.lockate.service.geofence.PlaceEvents
import com.peppeosmio.lockate.service.geofence.placeEventTexts
import com.peppeosmio.lockate.service.geofence.presenceText
import com.peppeosmio.lockate.service.location.Position
import com.peppeosmio.lockate.service.location.PositionProvider
import com.peppeosmio.lockate.service.push.namesUnknown
import com.peppeosmio.lockate.service.push.openPushPayload
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
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
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.ConnectException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

private val PAUSE_TIMEOUT = 10.seconds

// a group whose places keep changing while we post gives up until the next position
private const val MAX_PLACE_REFRESHES = 2

// A post on a connection that died silently (NAT dropped, no FIN) waits for an answer that never
// comes; it must fail while the phone is awake, so the next fix gets a new connection
private const val POST_TIMEOUT_MILLIS = 15_000L

// bounds a send (a post, its place refreshes and their reposts) if it never returns
private const val POST_WAKE_LOCK_MILLIS = 60_000L

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
    private val wakeLockService: WakeLockService,
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

    /** How many members each group has, as last known on this phone, by group */
    suspend fun countMembers(): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.countMembers().associate {
                it.anonymousGroupInternalId to it.memberCount
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
                    "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/members/leave"
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
                url("${connectionSettings.url}/api/v1/anonymous-groups")
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
                    "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroupId}/members/join"
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
            val snapshot =
                decryptSnapshot(body.anonymousGroup.id, groupKey, body.members, body.places)
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
                            snapshot.members
                                .filter { it.id != agMember.id }
                                .map {
                                    AGMemberMapper.domainToEntity(it, anonymousGroupInternalId = 0)
                                },
                    places = snapshot.places,
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
                    "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/members/auth/verify"
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

    private val _groupFrames = MutableSharedFlow<GroupFrame>(extraBufferCapacity = 64)

    /**
     * What happens in the groups whose stream is open, and the snapshots fetched for a push,
     * already applied to the local database.
     */
    val groupFrames = _groupFrames.asSharedFlow()

    private class GroupChannel {
        val status = MutableStateFlow(ChannelStatus())
    }

    private val groupLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * What the server sends about a group is applied and emitted under it, so a snapshot fetched
     * for a push can't interleave with the frames of a stream.
     */
    private fun groupLock(anonymousGroupId: String) =
        groupLocks.getOrPut(anonymousGroupId) { Mutex() }

    /**
     * Shares our location with the group for as long as the caller is suspended here: follows it,
     * runs the group's [GeofenceTracker] and posts each position with its place events. Only the
     * foreground service holds it, and only for the groups we share with. [onStatus] gets the state
     * of the posts and of the location.
     */
    suspend fun holdSharing(
        anonymousGroup: AnonymousGroup,
        onStatus: (ChannelStatus) -> Unit = {},
    ): Unit = coroutineScope {
        val channel = GroupChannel()
        val statusJob = launch { channel.status.collect { onStatus(it) } }
        // the saved places, so we geofence also when the server can't be reached
        val tracker =
            GeofenceTracker().apply { replacePlaces(listPlaces(anonymousGroup.internalId)) }
        try {
            sendLocationWhileEnabled(anonymousGroup, channel, tracker)
        } finally {
            statusJob.cancel()
        }
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

    /**
     * Follows the location and posts it. GPS faults are reported in the channel's status and the
     * sending resumes by itself.
     */
    private suspend fun sendLocationWhileEnabled(
        anonymousGroup: AnonymousGroup,
        channel: GroupChannel,
        tracker: GeofenceTracker,
    ) {
        val connectionSettings =
            connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        // we may have moved anywhere since the last time we shared the location
        tracker.forgetPosition()
        try {
            while (true) {
                try {
                    locationService.checkPermissionsAndLocationEnabled()
                    channel.status.update { it.copy(isLocationDisabled = false) }
                    sendLocation(anonymousGroup, connectionSettings, channel, tracker)
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
            // The sending stops only when we stop sharing (or the group is gone), so the others
            // learn it now instead of after the server's timeout. The location posts run in this
            // loop, so none can follow it. If it fails, the timeout tells them.
            withContext(NonCancellable) {
                withTimeoutOrNull(PAUSE_TIMEOUT) {
                    postAsMember(anonymousGroup, connectionSettings, "members/pause", null)
                }
            }
        }
    }

    /** Posts [position] and the place [transitions], computed on the places of [placesVersion]. */
    private suspend fun postLocation(
        anonymousGroup: AnonymousGroup,
        connectionSettings: Connection,
        position: Position,
        transitions: List<PlaceTransition>,
        placesVersion: Long?,
    ): HttpResponse? {
        val encrypted =
            payloadCodec.encryptMemberMessage(
                message =
                    MemberMessage(
                        coordinates = position.fix.coordinates,
                        activity = position.activity,
                    ),
                key = anonymousGroup.key,
                groupId = anonymousGroup.id,
                memberId = anonymousGroup.memberId,
            )
        val placeEvents = transitions.map {
            EncryptedDataMapper.toDto(
                payloadCodec.encryptPlaceEvent(
                    it,
                    anonymousGroup.key,
                    anonymousGroup.id,
                    anonymousGroup.memberId,
                )
            )
        }
        return postAsMember(
            anonymousGroup,
            connectionSettings,
            "locations",
            AGPostLocationReqDto(
                encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                encryptedActivity = EncryptedDataMapper.toDto(encrypted.activity),
                placeEvents = placeEvents,
                placesVersion = placesVersion,
            ),
        )
    }

    /**
     * Posts [body] to the group's [path] as us. Null when it didn't get through; a removal or a
     * deleted group is applied here, which stops the sharing. A 409 is the answer of a location
     * post whose place events were computed on places that changed, and is returned. A post that
     * failed on the network (timeout, connection closed) while the network is there is sent once
     * more on a new connection: the next fix may be 10 minutes away.
     */
    private suspend fun postAsMember(
        anonymousGroup: AnonymousGroup,
        connectionSettings: Connection,
        path: String,
        body: AGPostLocationReqDto?,
    ): HttpResponse? =
        try {
            suspend fun post() = httpClient.post {
                url("${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/$path")
                timeout { requestTimeoutMillis = POST_TIMEOUT_MILLIS }
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${anonymousGroup.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                    )
                }
                body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it)
                }
            }
            val response =
                try {
                    post()
                } catch (e: IOException) {
                    if (!connectivityService.isNetworkAvailable()) throw e
                    sendLoopLog.log("post $path ${anonymousGroup.id} again: ${e::class.simpleName}")
                    post()
                }
            when (response.status.value) {
                200,
                409 -> response
                401,
                403 -> {
                    setAGIsMemberFalse(anonymousGroup)
                    null
                }
                404 -> {
                    setAGExistsRemoteFalse(anonymousGroup)
                    null
                }
                else -> {
                    sendLoopLog.log("post $path ${anonymousGroup.id} ${response.status.value}")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendLoopLog.log("post $path ${anonymousGroup.id} failed ${e::class.simpleName}")
            null
        }

    @OptIn(ExperimentalTime::class)
    private suspend fun sendLocation(
        anonymousGroup: AnonymousGroup,
        connectionSettings: Connection,
        channel: GroupChannel,
        tracker: GeofenceTracker,
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

        var lastMode: Pair<UserActivity, Boolean>? = null
        // null until the first post: the places may have changed while we weren't sharing
        var placesVersion: Long? = null

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

        /** Whether the server got [position]; its place events are confirmed when it did */
        suspend fun send(position: Position): Boolean {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            var transitions = tracker.update(position.fix, nowMillis)
            var delivered = false

            // The events are computed on the places of `placesVersion`; when the server has
            // other places it refuses them (409) or, without events, says so in its answer. Then
            // we fetch the places and look at the same fix again: the events go out only computed
            // on the places the server has, and a new place we just crossed isn't missed.
            repeat(MAX_PLACE_REFRESHES + 1) {
                // the next position replaces this one, and carries the same transitions again
                val response =
                    postLocation(
                        anonymousGroup,
                        connectionSettings,
                        position,
                        transitions,
                        placesVersion,
                    ) ?: return delivered
                val version = response.body<AGPostLocationResDto>().placesVersion
                val sent = response.status.value == 200
                // The places are replaced before the post is confirmed: a changed place takes the
                // state of the last position the server got from us, before this one. A failed
                // fetch leaves the version as it was, so the next post tries again.
                val refreshed =
                    version != placesVersion &&
                        refreshSnapshot(anonymousGroup).also { ok ->
                            if (ok) {
                                placesVersion = version
                                tracker.replacePlaces(listPlaces(anonymousGroup.internalId))
                            }
                        }
                if (sent) {
                    delivered = true
                    tracker.confirm(transitions, nowMillis)
                    _events.tryEmit(
                        AnonymousGroupEvent.AGLocationSentEvent(
                            anonymousGroupInternalId = anonymousGroup.internalId,
                            anonymousGroupId = anonymousGroup.id,
                            connectionId = anonymousGroup.connectionId,
                            locationRecord =
                                LocationRecord(
                                    coordinates = position.fix.coordinates,
                                    timestamp = Clock.System.now().toLocalDateTime(TimeZone.UTC),
                                    activity = position.activity,
                                ),
                        )
                    )
                }
                if (!refreshed) return delivered
                transitions = tracker.update(position.fix, nowMillis)
                if (sent && transitions.isEmpty()) return true
            }
            return delivered
        }

        positionProvider
            .positions()
            .onEach { position ->
                val mode = position.activity to position.isFresh
                if (mode != lastMode) {
                    lastMode = mode
                    sendLoopLog.log(
                        "channel ${anonymousGroup.id} activity=${mode.first} fresh=${mode.second}"
                    )
                }
                channel.status.update { it.copy(isLocationUnavailable = false) }

                timeoutJob?.cancel()
                // while still the position comes every STILL_FIX_INTERVAL_MILLIS
                timeoutJob =
                    if (position.activity == UserActivity.Still) null
                    else
                        launch {
                            getTimeoutJob()
                        }
            }
            // while still the next position is 10 minutes away, so one lost offline is sent again
            // when the network returns
            .postEach(
                // A still phone sleeps again when the fix's wake lock times out, maybe mid-post.
                // The timeouts don't count the time the CPU sleeps, so a stuck post would wait out
                // its timeout over many 10-minute wakes, and nothing else is sent meanwhile.
                post = { wakeLockService.held("lockate:post", POST_WAKE_LOCK_MILLIS) { send(it) } },
                isNetworkAvailable = connectivityService::isNetworkAvailable,
                awaitNetworkAvailable = connectivityService::awaitNetworkAvailable,
            )
    }

    private suspend fun decryptSnapshot(
        anonymousGroupId: String,
        key: ByteArray,
        members: List<EncryptedAGMemberDto>,
        places: List<PlaceDto>,
    ) =
        AGChannelFrame.Snapshot(
            members =
                members.mapDecryptable(
                    onUndecryptable = {
                        Log.e("", "Skipped member ${it.id} of $anonymousGroupId: can't decrypt it")
                    }
                ) {
                    AGMemberMapper.dtoToDomain(it, payloadCodec, key, anonymousGroupId)
                },
            places =
                places.mapDecryptable(
                    onUndecryptable = {
                        Log.e("", "Skipped place ${it.id} of $anonymousGroupId: can't decrypt it")
                    }
                ) {
                    PlaceMapper.dtoToDomain(it, payloadCodec, key, anonymousGroupId)
                },
        )

    /**
     * Makes a snapshot, of the socket or of the locations stream, what the database holds of the
     * group. A member's position saved since is newer than the server's last saved one, so it
     * stays.
     */
    private suspend fun saveSnapshot(
        anonymousGroup: AnonymousGroup,
        members: List<EncryptedAGMemberDto>,
        places: List<PlaceDto>,
    ): AGChannelFrame.Snapshot {
        val internalId = anonymousGroup.internalId
        val decrypted = decryptSnapshot(anonymousGroup.id, anonymousGroup.key, members, places)
        val knownLocations =
            anonymousGroupDao.listAGMembers(internalId).associate {
                it.id to AGMemberMapper.entityToDomain(it).lastLocationRecord
            }
        val snapshot =
            decrypted.copy(
                members =
                    decrypted.members.map { member ->
                        val known = knownLocations[member.id] ?: return@map member
                        val record = member.lastLocationRecord
                        if (record == null || known.timestamp > record.timestamp) {
                            member.copy(lastLocationRecord = known)
                        } else member
                    }
            )
        snapshot.members
            .firstOrNull { it.id == anonymousGroup.memberId }
            ?.let { me ->
                if (me.isAGAdmin != anonymousGroup.memberIsAGAdmin) {
                    anonymousGroupDao.setAGMemberIsAGAdminSelf(internalId, me.isAGAdmin)
                }
            }
        anonymousGroupDao.setAGMembers(
            anonymousGroupInternalId = internalId,
            agMemberEntities =
                snapshot.members.map {
                    AGMemberMapper.domainToEntity(it, anonymousGroupInternalId = internalId)
                },
        )
        anonymousGroupDao.syncPlaces(internalId, snapshot.places)
        return snapshot
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

    private suspend fun handleFrame(anonymousGroup: AnonymousGroup, dto: AGFrameDto) {
        val internalId = anonymousGroup.internalId
        val frame: AGChannelFrame =
            try {
                when (dto.type) {
                    AGFrameDto.SNAPSHOT -> saveSnapshot(anonymousGroup, dto.members!!, dto.places!!)

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
                        if (dto.memberId == anonymousGroup.memberId) {
                            setAGIsMemberFalse(anonymousGroup)
                            return
                        }
                        anonymousGroupDao.deleteAGMemberById(dto.memberId!!, internalId)
                        AGChannelFrame.MemberLeft(dto.memberId)
                    }

                    AGFrameDto.GROUP_DELETED -> {
                        setAGExistsRemoteFalse(anonymousGroup)
                        return
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

                    AGFrameDto.PLACE_EVENT -> {
                        val memberId = dto.memberId!!
                        // the server never relays our own events, but if it did they mustn't tell
                        // us about ourselves
                        if (memberId == anonymousGroup.memberId) return
                        val placeEvent = dto.placeEvent!!
                        AGChannelFrame.PlaceEvent(
                            memberId = memberId,
                            transition =
                                payloadCodec.decryptPlaceEvent(
                                    EncryptedDataMapper.toDomain(placeEvent.encryptedEvent),
                                    anonymousGroup.key,
                                    anonymousGroup.id,
                                    memberId,
                                ),
                            timestamp = placeEvent.timestamp,
                        )
                    }

                    AGFrameDto.MEMBER_PRESENCE -> {
                        if (dto.memberId == anonymousGroup.memberId) return
                        AGChannelFrame.MemberPresence(dto.memberId!!, dto.online!!, dto.lastSeen)
                    }

                    AGFrameDto.PLACE_UPSERTED -> {
                        val place =
                            PlaceMapper.dtoToDomain(
                                dto.place!!,
                                payloadCodec,
                                anonymousGroup.key,
                                anonymousGroup.id,
                            )
                        anonymousGroupDao.upsertPlace(internalId, place)
                        AGChannelFrame.PlaceUpserted(place)
                    }

                    AGFrameDto.PLACE_DELETED -> {
                        anonymousGroupDao.deletePlace(dto.placeId!!, internalId)
                        AGChannelFrame.PlaceDeleted(dto.placeId)
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
     * Streams the group for as long as the caller is suspended here: first its snapshot, then its
     * changes and the others' place events and sharing changes, all emitted on [groupFrames], and
     * the live positions, handed to [onLocations]. Only the screens showing the group hold it, so
     * the phone receives the group live only while someone looks at it. Everything is saved locally
     * before it is handed over. A removal or a deleted group marks the group and ends it.
     */
    suspend fun holdGroupStream(
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
                                "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/locations"
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
                            when (event.event) {
                                "frame" -> {
                                    val dto = apiJson.decodeFromString<AGFrameDto>(event.data!!)
                                    groupLock(anonymousGroup.id).withLock {
                                        handleFrame(anonymousGroup, dto)
                                    }
                                    val ended =
                                        dto.type == AGFrameDto.GROUP_DELETED ||
                                            (dto.type == AGFrameDto.MEMBER_LEFT &&
                                                dto.memberId == anonymousGroup.memberId)
                                    if (ended) {
                                        onState(ChannelState.Stopped)
                                        awaitCancellation()
                                    }
                                }

                                "snapshot" -> {
                                    val dto = apiJson.decodeFromString<AGFrameDto>(event.data!!)
                                    groupLock(anonymousGroup.id).withLock {
                                        val snapshot =
                                            saveSnapshot(
                                                anonymousGroup,
                                                dto.members!!,
                                                dto.places!!,
                                            )
                                        _groupFrames.emit(
                                            GroupFrame(
                                                anonymousGroup.internalId,
                                                anonymousGroup.id,
                                                snapshot,
                                            )
                                        )
                                    }
                                }

                                "location" ->
                                    onLocations(
                                        saveLocations(
                                            anonymousGroup,
                                            listOf(
                                                apiJson.decodeFromString<MemberLocationDto>(
                                                    event.data!!
                                                )
                                            ),
                                        )
                                    )
                            }
                        }
                    }
                    throw IOException("Group stream closed by the server")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val response = (e as? SSEClientException)?.response
                    val failure = response?.let { responseFailure(it, e) } ?: e
                    when (failure) {
                        is UnauthorizedException -> setAGIsMemberFalse(anonymousGroup)
                        is RemoteAGNotFoundException -> setAGExistsRemoteFalse(anonymousGroup)
                    }
                    when (
                        classifyStreamFailure(failure, connectivityService.isNetworkAvailable())
                    ) {
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
     * the location with, and the others to be told about the places.
     */
    /**
     * Shares our location with every group we share with, for as long as the caller is suspended
     * here, following the groups that start or stop being shared. [onStatusUpdate] gets the state
     * of them all; no group means there is nothing left to do.
     */
    suspend fun holdAllSharing(onStatusUpdate: (SendLocationStatus) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val jobs = mutableMapOf<String, Job>()
            val statuses = mutableMapOf<String, ChannelStatus>()

            fun emitStatus() {
                val snapshot = synchronized(statuses) { statuses.values.toList() }
                onStatusUpdate(
                    SendLocationStatus(
                        sharedAGCount = snapshot.size,
                        isLocationOff =
                            snapshot.any { it.isLocationDisabled || it.isLocationUnavailable },
                    )
                )
            }

            fun stop(anonymousGroupId: String) {
                jobs.remove(anonymousGroupId)?.cancel()
                synchronized(statuses) { statuses.remove(anonymousGroupId) }
                emitStatus()
            }

            suspend fun start(entity: AnonymousGroupEntity) {
                val anonymousGroup =
                    AnonymousGroupMapper.toDomain(
                        entity = entity,
                        keyStoreService = keyStoreService,
                    )
                jobs.remove(anonymousGroup.id)?.cancel()
                if (
                    !anonymousGroup.sendLocation ||
                        !anonymousGroup.isMember ||
                        !anonymousGroup.existsRemote
                ) {
                    stop(anonymousGroup.id)
                    return
                }
                synchronized(statuses) { statuses[anonymousGroup.id] = ChannelStatus() }
                jobs[anonymousGroup.id] = launch {
                    holdSharing(anonymousGroup) { status ->
                        synchronized(statuses) { statuses[anonymousGroup.id] = status }
                        emitStatus()
                    }
                }
                emitStatus()
            }

            suspend fun startByInternalId(anonymousGroupInternalId: Long) {
                anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)?.let { start(it) }
            }

            // Listed once subscribed, so a group created, left or toggled meanwhile isn't missed;
            // an event about a group the listing already handled just starts or stops it again
            events
                .onSubscription {
                    anonymousGroupDao.listAGsToShare().forEach { start(it) }
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

    private suspend fun placeRequest(
        connectionSettingsId: Long,
        anonymousGroupInternalId: Long,
        placeId: String,
        method: HttpMethod,
        body: PlaceUpsertReqDto?,
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
                "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/places/$placeId"
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

    suspend fun listPlaces(anonymousGroupInternalId: Long): List<Place> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.listPlaces(anonymousGroupInternalId).map {
                PlaceMapper.entityToDomain(it)
            }
        }

    suspend fun upsertPlace(
        connectionSettingsId: Long,
        anonymousGroup: AnonymousGroup,
        place: Place,
    ) =
        withContext(Dispatchers.IO) {
            val encrypted = payloadCodec.encryptPlace(place, anonymousGroup.key, anonymousGroup.id)
            placeRequest(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupInternalId = anonymousGroup.internalId,
                placeId = place.id,
                method = HttpMethod.Put,
                body =
                    PlaceUpsertReqDto(
                        encryptedCoordinates = EncryptedDataMapper.toDto(encrypted.coordinates),
                        encryptedRadius = EncryptedDataMapper.toDto(encrypted.radius),
                        encryptedName = EncryptedDataMapper.toDto(encrypted.name),
                    ),
            )
            anonymousGroupDao.upsertPlace(anonymousGroup.internalId, place)
            _groupFrames.emit(
                GroupFrame(
                    anonymousGroup.internalId,
                    anonymousGroup.id,
                    AGChannelFrame.PlaceUpserted(place),
                )
            )
        }

    suspend fun deletePlace(
        connectionSettingsId: Long,
        anonymousGroup: AnonymousGroup,
        placeId: String,
    ) =
        withContext(Dispatchers.IO) {
            placeRequest(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupInternalId = anonymousGroup.internalId,
                placeId = placeId,
                method = HttpMethod.Delete,
                body = null,
            )
            anonymousGroupDao.deletePlace(placeId, anonymousGroup.internalId)
            _groupFrames.emit(
                GroupFrame(
                    anonymousGroup.internalId,
                    anonymousGroup.id,
                    AGChannelFrame.PlaceDeleted(placeId),
                )
            )
        }

    /**
     * What to notify for a live frame, with the group and the member: a place event or a member
     * that stopped or started sharing. Both go in the member's one notification.
     */
    suspend fun describeNotification(groupFrame: GroupFrame): PlaceEvents? =
        withContext(Dispatchers.IO) {
            val memberId =
                when (val frame = groupFrame.frame) {
                    is AGChannelFrame.PlaceEvent -> frame.memberId
                    is AGChannelFrame.MemberPresence -> frame.memberId
                    else -> return@withContext null
                }
            val group =
                anonymousGroupDao.getAGByInternalId(groupFrame.anonymousGroupInternalId)
                    ?: return@withContext null
            val member =
                anonymousGroupDao.getAGMemberByIdAndAGInternalId(
                    memberId,
                    groupFrame.anonymousGroupInternalId,
                ) ?: return@withContext null
            val mutedTargets = listMutedTargets(group.internalId)
            val texts =
                when (val frame = groupFrame.frame) {
                    is AGChannelFrame.PlaceEvent ->
                        placeEventTexts(
                            memberId = member.id,
                            memberName = member.name,
                            transitions = listOf(frame.transition),
                            places = listPlaces(group.internalId).associateBy { it.id },
                            mutedTargets = mutedTargets,
                        )

                    is AGChannelFrame.MemberPresence ->
                        if (member.id in mutedTargets) emptyList()
                        else {
                            listOf(
                                presenceText(
                                    member.name,
                                    frame.online,
                                    frame.lastSeen,
                                    TimeZone.currentSystemDefault(),
                                )
                            )
                        }

                    else -> return@withContext null
                }
            if (texts.isEmpty()) null else PlaceEvents(group.id, group.name, member.id, texts)
        }

    /**
     * A push from a server, `{r, iv, c}`: the frame sealed with the key of the registration [r]
     * names (see `PushRegistrationService`). Applies a removal or a deleted group, and returns what
     * to notify for a place event or a sharing change. Null for anything that doesn't open: an
     * unknown registration (a group we left) or a push that wasn't sealed with our key.
     */
    suspend fun openPush(data: Map<String, String>): PlaceEvents? =
        withContext(Dispatchers.IO) {
            val r = data["r"] ?: return@withContext null
            val iv = data["iv"] ?: return@withContext null
            val c = data["c"] ?: return@withContext null
            val dto =
                try {
                    val registration =
                        anonymousGroupDao.getPushRegistrationById(Base64.decode(r))
                            ?: return@withContext null
                    val key =
                        keyStoreService.decrypt(
                            EncryptedData(registration.keyCipher, registration.keyIv)
                        )
                    val plaintext = openPushPayload(cryptoService, key, r, iv, c)
                    registration.anonymousGroupInternalId to
                        apiJson.decodeFromString<AGFrameDto>(plaintext.decodeToString())
                } catch (e: IllegalArgumentException) {
                    return@withContext null
                } catch (e: CryptoException) {
                    return@withContext null
                }
            val (internalId, frame) = dto
            val entity = anonymousGroupDao.getAGByInternalId(internalId) ?: return@withContext null
            val group = AnonymousGroupMapper.toDomain(entity, keyStoreService)
            val channelFrame =
                when (frame.type) {
                    AGFrameDto.GROUP_DELETED -> {
                        setAGExistsRemoteFalse(group)
                        return@withContext null
                    }

                    AGFrameDto.MEMBER_LEFT -> {
                        if (frame.memberId == group.memberId) setAGIsMemberFalse(group)
                        return@withContext null
                    }

                    AGFrameDto.PLACE_EVENT ->
                        AGChannelFrame.PlaceEvent(
                            memberId = frame.memberId!!,
                            transition =
                                payloadCodec.decryptPlaceEvent(
                                    EncryptedDataMapper.toDomain(frame.placeEvent!!.encryptedEvent),
                                    group.key,
                                    group.id,
                                    frame.memberId,
                                ),
                            timestamp = frame.placeEvent.timestamp,
                        )

                    AGFrameDto.MEMBER_PRESENCE ->
                        AGChannelFrame.MemberPresence(
                            frame.memberId!!,
                            frame.online!!,
                            frame.lastSeen,
                        )

                    else -> return@withContext null
                }
            val known = anonymousGroupDao.listAGMembers(group.internalId).map { it.id }.toSet()
            val places = listPlaces(group.internalId).map { it.id }.toSet()
            if (namesUnknown(channelFrame, known, places)) {
                refreshSnapshot(group)
            }
            describeNotification(GroupFrame(group.internalId, group.id, channelFrame))
        }

    /**
     * Fetches and saves the group's snapshot: for a push naming a member or place the phone hasn't
     * saved yet, and when a location post says the places changed. False if the server couldn't be
     * reached.
     */
    private suspend fun refreshSnapshot(anonymousGroup: AnonymousGroup): Boolean {
        val connectionSettings =
            connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        val dto =
            try {
                val response = httpClient.get {
                    url(
                        "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/snapshot"
                    )
                    headers {
                        connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                        append(
                            HttpHeaders.Authorization,
                            "AGMember ${anonymousGroup.memberId} ${Base64.encode(anonymousGroup.memberToken)}",
                        )
                    }
                }
                if (!response.status.isSuccess()) {
                    Log.w(
                        "",
                        "Can't fetch the snapshot of ${anonymousGroup.id}: ${response.status}",
                    )
                    return false
                }
                response.body<AGFrameDto>()
            } catch (e: IOException) {
                Log.w("", "Can't fetch the snapshot of ${anonymousGroup.id}", e)
                return false
            }
        groupLock(anonymousGroup.id).withLock {
            val snapshot = saveSnapshot(anonymousGroup, dto.members!!, dto.places!!)
            _groupFrames.emit(GroupFrame(anonymousGroup.internalId, anonymousGroup.id, snapshot))
        }
        return true
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
                url("${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}")
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
                    "${connectionSettings.url}/api/v1/anonymous-groups/${anonymousGroup.id}/members/$agMemberId/promote"
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
