package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGLocationUpdateMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGMemberMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMemberPasswordSrpInfoResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMembersResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGLocationSaveReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.LocationUpdateDto
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.exceptions.AGAdminTokenInvalidException
import com.peppeosmio.lockate.exceptions.AGAdminUnauthorizedException
import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.Base64Exception
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.srp.SrpClientService
import com.peppeosmio.lockate.utils.ErrorHandler
import dev.whyoleg.cryptography.bigint.decodeToBigInt
import dev.whyoleg.cryptography.bigint.encodeToByteArray
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.ConnectException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.io.encoding.Base64
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AnonymousGroupService(
    private val anonymousGroupDao: AnonymousGroupDao,
    private val cryptoService: CryptoService,
    private val connectionService: ConnectionService,
    private val httpClient: HttpClient,
    private val srpClientService: SrpClientService,
    private val locationService: LocationService,
    private val keyStoreService: KeyStoreService
) {
    private val _events = MutableSharedFlow<AnonymousGroupEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()
    private val sendLocationJobs = mutableMapOf<String, Job>()

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
        memberPassword: String,
        adminPassword: String
    ): AnonymousGroup = withContext(Dispatchers.IO) {
        val keySalt = cryptoService.getKeySalt()
        val passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
        val key = cryptoService.createKey(passwordBytes = passwordBytes, keySalt = keySalt)
        val encryptedMemberName = cryptoService.encrypt(
            data = memberName.toByteArray(Charsets.UTF_8), key = key
        )
        val encryptedMemberNameDto = EncryptedDataMapper.toDto(encryptedMemberName)
        val memberSrpSalt = cryptoService.getKeySalt()
        val memberSrpVerifier = srpClientService.generateVerifier(
            identifier = groupName, password = memberPassword, salt = memberSrpSalt
        )
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
                    memberPasswordSrpVerifier = Base64.encode(memberSrpVerifier.toByteArray()),
                    memberPasswordSrpSalt = Base64.encode(memberSrpSalt),
                    adminPassword = adminPassword,
                    keySalt = Base64.encode(keySalt)
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
            key = key
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
            adminToken = null,
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
            adminToken = null,
            isMember = true,
            existsRemote = true,
            sendLocation = true,
            key = key,
            connectionId = connectionId
        )
    }

    @Throws(
        ConnectionSettingsNotFoundException::class,
        SerializationException::class,
        RemoteAGNotFoundException::class,
        UnauthorizedException::class,
        ConnectException::class
    )
    suspend fun authMember(
        connectionId: Long, anonymousGroupId: String, memberName: String, memberPassword: String
    ): String = withContext(Dispatchers.IO) {
        val agEntity = anonymousGroupDao.getAGByIdAndConnectionId(
            anonymousGroupId = anonymousGroupId, connectionId = connectionId
        )
        if (agEntity != null) {
            throw LocalAGExistsException()
        }
        val srpClient = srpClientService.getSrpClient()
        val connectionSettings = connectionService.getConnectionSettingsById(connectionId)
        val agGetMemberPasswordSrpInfoResponse = httpClient.get {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/srp/info")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
            }
        }
        when (agGetMemberPasswordSrpInfoResponse.status.value) {
            200 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(agGetMemberPasswordSrpInfoResponse)
            404 -> throw RemoteAGNotFoundException()
            else -> ErrorHandler.handleGeneric(agGetMemberPasswordSrpInfoResponse)
        }
        val agGetMemberPasswordSrpInfoResponseBody =
            agGetMemberPasswordSrpInfoResponse.body<AGGetMemberPasswordSrpInfoResDto>()
        val agName: String
        val keySalt = Base64.decode(agGetMemberPasswordSrpInfoResponseBody.keySalt)
        val key = cryptoService.createKey(
            passwordBytes = memberPassword.toByteArray(Charsets.UTF_8), keySalt = keySalt
        )
        try {
            agName = cryptoService.decrypt(
                encryptedData = EncryptedDataMapper.toDomain(agGetMemberPasswordSrpInfoResponseBody.encryptedName),
                key = key
            ).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.d("", "Anonymous group encrypted name decryption failed.")
            throw UnauthorizedException()
        }
        val A = srpClientService.getA(
            srpClient = srpClient,
            salt = agGetMemberPasswordSrpInfoResponseBody.srpSalt,
            identifier = agName,
            password = memberPassword
        )

        val memberAuthStartResponse = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/srp/start")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody<AGMemberAuthStartReqDto>(
                AGMemberAuthStartReqDto(
                    A = Base64.encode(A.encodeToByteArray())
                )
            )
        }
        when (memberAuthStartResponse.status.value) {
            200 -> Unit
            400 -> throw Base64Exception()
            401, 403 -> ErrorHandler.handleUnauthorized(memberAuthStartResponse)
            404 -> throw RemoteAGNotFoundException()
            else -> ErrorHandler.handleGeneric(memberAuthStartResponse)
        }
        val memberAuthStartResponseBody = memberAuthStartResponse.body<AGMemberAuthStartResDto>()
        val M1 = srpClientService.getM1(
            srpClient = srpClient, B = Base64.decode(memberAuthStartResponseBody.B).decodeToBigInt()
        )
        val encryptedMemberName = cryptoService.encrypt(
            data = memberName.toByteArray(Charsets.UTF_8), key = key
        )
        val memberAuthVerifyResponse = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/srp/verify")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody<AGMemberAuthVerifyReqDto>(
                AGMemberAuthVerifyReqDto(
                    encryptedMemberName = EncryptedDataMapper.toDto(encryptedMemberName),
                    srpSessionId = memberAuthStartResponseBody.srpSessionId,
                    M1 = Base64.encode(M1.encodeToByteArray()),
                )
            )
        }
        when (memberAuthVerifyResponse.status.value) {
            200 -> Unit
            400 -> throw Base64Exception()
            401, 403 -> ErrorHandler.handleUnauthorized(memberAuthVerifyResponse)
            404 -> throw RemoteAGNotFoundException()
            else -> ErrorHandler.handleGeneric(memberAuthVerifyResponse)
        }
        val memberAuthVerifyResBody = memberAuthVerifyResponse.body<AGMemberAuthVerifyResDto>()
        val agMember = AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = memberAuthVerifyResBody.authenticatedMemberInfo.member,
            cryptoService = cryptoService,
            key = key
        )
        val agMemberEntity = AGMemberMapper.domainToEntity(
            agMember = agMember, anonymousGroupInternalId = 0
        )
        val anonymousGroupInternalId = anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupMapper.toEntity(
                anonymousGroup = AnonymousGroup(
                    internalId = 0,
                    id = memberAuthVerifyResBody.anonymousGroup.id,
                    name = agName,
                    createdAt = memberAuthVerifyResBody.anonymousGroup.createdAt,
                    joinedAt = memberAuthVerifyResBody.authenticatedMemberInfo.member.createdAt,
                    memberName = cryptoService.decrypt(
                        encryptedData = EncryptedDataMapper.toDomain(memberAuthVerifyResBody.authenticatedMemberInfo.member.encryptedName),
                        key = key
                    ).toString(Charsets.UTF_8),
                    memberId = agMemberEntity.id,
                    isMember = true,
                    existsRemote = true,
                    sendLocation = true,
                    memberToken = Base64.decode(memberAuthVerifyResBody.authenticatedMemberInfo.token),
                    adminToken = null,
                    key = key,
                    connectionId = connectionId
                ), keyStoreService = keyStoreService
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.tryEmit(
            AnonymousGroupEvent.NewAnonymousGroupEvent(
                anonymousGroupInternalId = anonymousGroupInternalId,
                anonymousGroupId = memberAuthVerifyResBody.anonymousGroup.id,
                connectionId = connectionId
            )
        )
        memberAuthVerifyResBody.authenticatedMemberInfo.token
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

    suspend fun getRemoteAGMembers(
        connectionSettingsId: Long, anonymousGroupInternalId: Long
    ): List<AGMember> = withContext(Dispatchers.IO) {
        val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val response = httpClient.get {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/members")
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
        } catch (e: UnauthorizedException) {
            setAGIsMemberFalse(anonymousGroup)
            throw AGMemberUnauthorizedException()
        } catch (e: RemoteAGNotFoundException) {
            setAGExistsRemoteFalse(anonymousGroup)
            throw e
        }
        val encryptedMembers = response.body<AGGetMembersResDto>().members
        val decryptedMembers = encryptedMembers.map {
            async {
                AGMemberMapper.dtoToDomain(
                    encryptedAGMemberDto = it,
                    cryptoService = cryptoService,
                    key = anonymousGroup.key
                )
            }
        }.awaitAll()
        Log.d("", "Decrypted members: ${decryptedMembers.map { it.id }}")
        anonymousGroupDao.setAGMembers(
            anonymousGroupInternalId = anonymousGroupInternalId,
            agMemberEntities = decryptedMembers.map {
                AGMemberMapper.domainToEntity(
                    agMember = it, anonymousGroupInternalId = it.internalId
                )
            })
        decryptedMembers
    }

    suspend fun verifyAdminAuth(connectionSettingsId: Long, anonymousGroupInternalId: Long) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            if (anonymousGroup.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/admin/auth/verify")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGAdmin ${Base64.encode(anonymousGroup.adminToken)}"
                    )
                    append(HttpHeaders.ContentType, "application/json")
                }
            }
            try {
                when (response.status.value) {
                    200 -> Unit
                    401, 403 -> ErrorHandler.handleUnauthorized(response)
                    404 -> throw RemoteAGNotFoundException()
                    else -> ErrorHandler.handleGeneric(response)
                }
            } catch (e: RemoteAGNotFoundException) {
                setAGExistsRemoteFalse(anonymousGroup)
                throw e
            } catch (e: UnauthorizedException) {
                throw AGAdminTokenInvalidException()
            }
        }

    suspend fun getAdminToken(
        connectionSettingsId: Long, anonymousGroupInternalId: Long, adminPassword: String
    ) = withContext(Dispatchers.IO) {
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val response = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/admin/auth/token")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(AGAdminAuthReqDto(adminPassword = adminPassword))
        }
        try {
            when (response.status.value) {
                201 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
        } catch (e: RemoteAGNotFoundException) {
            setAGExistsRemoteFalse(anonymousGroup)
            throw e
        } catch (e: UnauthorizedException) {
            throw AGAdminUnauthorizedException()
        }
        val responseBody = response.body<AGAdminAuthResDto>()
        val encryptedAdminToken = keyStoreService.encrypt(Base64.decode(responseBody.adminToken))
        anonymousGroupDao.setAGAdminToken(
            anonymousGroupInternalId = anonymousGroupInternalId,
            adminTokenCipher = encryptedAdminToken.cipherText,
            adminTokenIv = encryptedAdminToken.iv,
        )
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

    @OptIn(ExperimentalTime::class)
    private suspend fun anonymousGroupSendLocation(
        anonymousGroup: AnonymousGroup, onConnected: () -> Unit, onDisconnected: () -> Unit
    ) {
        val connectionSettings =
            connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        val maxRetries = 10
        var retries = 0
        while (true) {
            var isConnected = false
            try {
                httpClient.webSocket(request = {
                    url("${connectionSettings.getWebSocketUrl()}/api/ws/anonymous-groups/${anonymousGroup.id}/send-location")
                    headers {
                        connectionSettings.apiKey?.let { ak ->
                            append(
                                "X-API-KEY", ak
                            )
                        }
                        append(
                            HttpHeaders.Authorization, "AGMember ${anonymousGroup.memberId} ${
                                Base64.encode(
                                    anonymousGroup.memberToken
                                )
                            }"
                        )
                        append(
                            HttpHeaders.ContentType, "application/json"
                        )
                    }
                }) {
                    isConnected = true
                    retries = 0
                    onConnected()
                    Log.i(
                        "",
                        "Sending AG location to ${connectionSettings.url} agId: ${anonymousGroup.id} agMemberId: ${anonymousGroup.memberId}"
                    )
                    coroutineScope {
                        suspend fun getTimeoutJob() {
                            delay(30000L)
                            Log.e("", "No location obtained in the last 30 s")
                            throw LocationDisabledException()
                        }

                        var timeoutJob = launch {
                            getTimeoutJob()
                        }
                        locationService.getLocationUpdates().collect { coordinates ->
                            timeoutJob.cancel()
                            timeoutJob = launch {
                                getTimeoutJob()
                            }
                            val coordinatesBytes = coordinates.toByteArray()
                            val encryptedCoordinates = cryptoService.encrypt(
                                data = coordinatesBytes, key = anonymousGroup.key
                            )
                            send(
                                Frame.Text(
                                    text = Json.encodeToString(
                                        AGLocationSaveReqDto(
                                            encryptedLocation = EncryptedDataMapper.toDto(
                                                encryptedCoordinates
                                            )
                                        )
                                    )
                                )
                            )
//                                Log.d(
//                                    "",
//                                    "Sent location $coordinates to AG ${anonymousGroup.id} connection ${connectionSettings.url}"
//                                )
                            _events.tryEmit(
                                AnonymousGroupEvent.AGLocationSentEvent(
                                    anonymousGroupInternalId = anonymousGroup.internalId,
                                    anonymousGroupId = anonymousGroup.id,
                                    connectionId = anonymousGroup.connectionId,
                                    timestamp = Clock.System.now()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    onDisconnected()
                }
                when (e) {
                    is ResponseException -> {
                        val response = e.response
                        try {
                            when (response.status.value) {
                                401, 403 -> ErrorHandler.handleUnauthorized(response)
                                404 -> throw RemoteAGNotFoundException()
                                else -> ErrorHandler.handleGeneric(response)
                            }
                        } catch (ee: Exception) {
                            ee.printStackTrace()
                            when (ee) {
                                is UnauthorizedException -> {
                                    setAGIsMemberFalse(anonymousGroup)
                                    return
                                }

                                is RemoteAGNotFoundException -> {
                                    setAGExistsRemoteFalse(
                                        anonymousGroup
                                    )
                                    return
                                }
                            }
                        }
                    }

                    is LocationDisabledException -> {}

                    else -> {
                        e.printStackTrace()
                    }
                }
                retries += 1
                retries = min(retries, maxRetries)
                val waitSeconds = 5L * retries
                Log.e("", "Can't connect to ${connectionSettings.url} retrying in $waitSeconds s")
                delay(waitSeconds.seconds)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun sendLocation(updateActiveAGCount: (count: Int) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val anonymousGroupEntities = anonymousGroupDao.listAGsToSendLocation()
            val anonymousGroups = anonymousGroupEntities.map {
                AnonymousGroupMapper.toDomain(
                    entity = it, keyStoreService = keyStoreService
                )
            }
            val connectedAGCount = AtomicInt(0)

            val onConnecting = fun() {
                val count = connectedAGCount.incrementAndFetch()
                updateActiveAGCount(count)
            }

            // this is also called when a Job is canceled since it gets JobCancellationException
            val onDisconnected = fun() {
                val count = connectedAGCount.decrementAndFetch()
                updateActiveAGCount(count)
            }

            anonymousGroups.forEach {
                sendLocationJobs[it.id]?.cancel()
                sendLocationJobs[it.id] = launch {
                    anonymousGroupSendLocation(
                        anonymousGroup = it,
                        onConnected = onConnecting,
                        onDisconnected = onDisconnected
                    )
                }
            }

            events.collect { event ->
                when (event) {
                    is AnonymousGroupEvent.NewAnonymousGroupEvent -> {
                        val anonymousGroupEntity =
                            anonymousGroupDao.getAGByInternalId(event.anonymousGroupInternalId)
                                ?: return@collect
                        val anonymousGroup = AnonymousGroupMapper.toDomain(
                            entity = anonymousGroupEntity, keyStoreService = keyStoreService
                        )
                        sendLocationJobs[anonymousGroup.id] = launch {
                            anonymousGroupSendLocation(
                                anonymousGroup,
                                onConnected = onConnecting,
                                onDisconnected = onDisconnected
                            )
                        }
                    }

                    is AnonymousGroupEvent.SendEnabledEvent -> {
                        val anonymousGroupEntity =
                            anonymousGroupDao.getAGByInternalId(event.anonymousGroupInternalId)
                                ?: return@collect
                        val anonymousGroup = AnonymousGroupMapper.toDomain(
                            entity = anonymousGroupEntity, keyStoreService = keyStoreService
                        )
                        sendLocationJobs[anonymousGroup.id] = launch {
                            anonymousGroupSendLocation(
                                anonymousGroup,
                                onConnected = onConnecting,
                                onDisconnected = onDisconnected
                            )
                        }
                    }

                    is AnonymousGroupEvent.DeleteAnonymousGroupEvent -> {
                        sendLocationJobs.remove(event.anonymousGroupId)?.cancel()
                    }

                    is AnonymousGroupEvent.RemovedFromAnonymousGroupEvent -> {
                        sendLocationJobs.remove(event.anonymousGroupId)?.cancel()

                    }

                    is AnonymousGroupEvent.RemoteAGDoesntExistEvent -> {
                        sendLocationJobs.remove(event.anonymousGroupId)?.cancel()

                    }

                    is AnonymousGroupEvent.SendDisabledEvent -> {
                        sendLocationJobs.remove(event.anonymousGroupId)?.cancel()
                    }

                    else -> Unit
                }
            }
        }

    @OptIn(ExperimentalTime::class)
    suspend fun streamLocations(
        connectionSettingsId: Long, anonymousGroupInternalId: Long
    ): Flow<AGLocationUpdate> = withContext(Dispatchers.IO) {
        val connectionSettings = connectionService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        callbackFlow {
            httpClient.sse(
                "${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/locations",
                request = {
                    headers {
                        connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                        append(
                            HttpHeaders.Authorization,
                            "AGMember ${agEntity.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
                        )
                        append(HttpHeaders.ContentType, "application/json")
                    }
                },
                deserialize = { typeInfo, s ->
                    val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                    Json.decodeFromString(deserializer = serializer, string = s)
                }) {
                incoming.collect { event ->
                    when (event.event) {
                        "location" -> {
                            if (event.data == null) {
                                Unit
                            } else {
                                val agLocationUpdate = deserialize<LocationUpdateDto>(event.data)

                                if (agLocationUpdate != null) {
                                    val agLocation = AGLocationUpdateMapper.toDomain(
                                        locationUpdateDto = agLocationUpdate,
                                        cryptoService = cryptoService,
                                        key = anonymousGroup.key
                                    )
                                    Log.d(
                                        "", "Received location $agLocation"
                                    )
                                    val agMember = anonymousGroupDao.getAGMemberByIdAndAGInternalId(
                                        agInternalId = anonymousGroup.internalId,
                                        id = agLocation.agMemberId,
                                    ) ?: return@collect
                                    anonymousGroupDao.setAGMemberLastLocation(
                                        agMemberInternalId = agMember.internalId,
                                        lastLatitude = agLocation.locationRecord.coordinates.latitude,
                                        lastLongitude = agLocation.locationRecord.coordinates.longitude,
                                        lastSeen = agLocation.locationRecord.timestamp.toInstant(
                                            TimeZone.UTC
                                        ).toEpochMilliseconds()
                                    )
                                    send(
                                        agLocation
                                    )
                                }
                            }

                        }
                    }
                }
            }
            awaitClose {}
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
            if (anonymousGroup.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.delete {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGAdmin ${Base64.encode(anonymousGroup.adminToken)}"
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
}
