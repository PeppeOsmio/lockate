package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGLocationUpdateMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.AGMemberMapper
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMemberPasswordSrpInfoResDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMembersResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGLocationSaveRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGLocationUpdateDto
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.exceptions.AGAdminTokenInvalidException
import com.peppeosmio.lockate.exceptions.AGAdminUnauthorizedException
import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.Base64Exception
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.srp.SrpClientService
import com.peppeosmio.lockate.utils.ErrorHandler
import dev.whyoleg.cryptography.bigint.decodeToBigInt
import dev.whyoleg.cryptography.bigint.encodeToByteArray
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
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
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.ConnectException
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AnonymousGroupService(
    private val anonymousGroupDao: AnonymousGroupDao,
    private val cryptoService: CryptoService,
    private val connectionSettingsService: ConnectionSettingsService,
    private val httpClient: HttpClient,
    private val srpClientService: SrpClientService,
    private val locationService: LocationService,
    private val keyStoreService: KeyStoreService
) {
    private val _events = MutableSharedFlow<AnonymousGroupEvent>()
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

    suspend fun getAnonymousGroupById(anonymousGroupId: String): AnonymousGroup =
        withContext(Dispatchers.IO) {
            val entity = anonymousGroupDao.getAnonymousGroupById(
                anonymousGroupId
            ) ?: throw LocalAGNotFoundException()
            AnonymousGroupMapper.toDomain(
                entity = entity, keyStoreService = keyStoreService
            )
        }

    suspend fun getLocalAGMembers(anonymousGroupId: String): List<AGMember> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            anonymousGroupDao.listAGMembers(anonymousGroupId).map {
                AGMemberMapper.entityToDomain(it)
            }
        }

    suspend fun deleteLocalAnonymousGroup(anonymousGroupId: String) = withContext(Dispatchers.IO) {
        anonymousGroupDao.deleteAnonymousGroupById(anonymousGroupId)
        _events.emit(AnonymousGroupEvent.DeleteAnonymousGroupEvent(anonymousGroupId))
    }

    suspend fun setAGShareLocation(anonymousGroupId: String, sendLocation: Boolean) {
        anonymousGroupDao.setAGSendLocation(
            anonymousGroupId = anonymousGroupId, sendLocation = sendLocation
        )
    }

    private suspend fun setAGIsMemberFalse(anonymousGroupId: String) = withContext(Dispatchers.IO) {
        anonymousGroupDao.setAGIsMemberFalse(anonymousGroupId)
        _events.emit(AnonymousGroupEvent.RemovedFromAnonymousGroupEvent(anonymousGroupId))
    }

    private suspend fun setAGExistsRemoteFalse(anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.setAGExistsRemoteFalse(anonymousGroupId)
            _events.emit(AnonymousGroupEvent.RemoteAGDoesntExistEvent(anonymousGroupId))
        }

    @Throws(
        UnauthorizedException::class,
        InvalidApiKeyException::class,
        RemoteAGNotFoundException::class,
        APIException::class
    )
    suspend fun leaveAnonymousGroup(anonymousGroupId: String, connectionSettingsId: Long) =
        withContext(Dispatchers.IO) {

            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )

            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val response = httpClient.post {
                url("${connectionSettings.url}/api/anonymous-groups/$anonymousGroupId/members/leave")
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
                deleteLocalAnonymousGroup(anonymousGroupId)
                throw e
            } catch (e: UnauthorizedException) {
                setAGIsMemberFalse(anonymousGroupId)
                throw AGMemberUnauthorizedException()
            }
            deleteLocalAnonymousGroup(anonymousGroupId)
        }


    @OptIn(ExperimentalTime::class)
    @Throws(
        ConnectionSettingsNotFoundException::class,
        SerializationException::class,
        UnauthorizedException::class,
        ConnectException::class
    )
    suspend fun createAnonymousGroup(
        connectionSettingsId: Long,
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
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
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
                AGCreateRequestDto(
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

        val createResponseBody = createResponse.body<AGCreateResponseDto>()
        val agMember = AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = createResponseBody.authenticatedMemberInfo.member,
            cryptoService = cryptoService,
            key = key
        )
        val agMemberEntity = AGMemberMapper.domainToEntity(
            agMember = agMember,
            anonymousGroupId = createResponseBody.anonymousGroup.id,
        )
        val anonymousGroup = AnonymousGroup(
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
            key = key
        )
        anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupMapper.toEntity(
                anonymousGroup = anonymousGroup,
                connectionSettingsId = connectionSettingsId,
                keyStoreService = keyStoreService
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.emit(AnonymousGroupEvent.NewAnonymousGroupEvent(createResponseBody.anonymousGroup.id))
        AnonymousGroup(
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
            key = key
        )
    }

    @OptIn(ExperimentalTime::class)
    @Throws(
        ConnectionSettingsNotFoundException::class,
        SerializationException::class,
        RemoteAGNotFoundException::class,
        UnauthorizedException::class,
        ConnectException::class
    )
    suspend fun authMember(
        connectionSettingsId: Long,
        anonymousGroupId: String,
        memberName: String,
        memberPassword: String
    ): String = withContext(Dispatchers.IO) {
        val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
        if (agEntity != null) {
            throw LocalAGExistsException()
        }
        val srpClient = srpClientService.getSrpClient()
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
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
            setBody<AGMemberAuthStartRequestDto>(
                AGMemberAuthStartRequestDto(
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
        val memberAuthStartResponseBody =
            memberAuthStartResponse.body<AGMemberAuthStartResponseDto>()
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
            setBody<AGMemberAuthVerifyRequestDto>(
                AGMemberAuthVerifyRequestDto(
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
        val memberAuthVerifyResBody = memberAuthVerifyResponse.body<AGMemberAuthVerifyResponseDto>()
        val agMember = AGMemberMapper.dtoToDomain(
            encryptedAGMemberDto = memberAuthVerifyResBody.authenticatedMemberInfo.member,
            cryptoService = cryptoService,
            key = key
        )
        val agMemberEntity = AGMemberMapper.domainToEntity(
            agMember = agMember, anonymousGroupId = anonymousGroupId
        )
        anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupMapper.toEntity(
                anonymousGroup = AnonymousGroup(
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
                    key = key
                ), keyStoreService = keyStoreService, connectionSettingsId = connectionSettingsId
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.emit(AnonymousGroupEvent.NewAnonymousGroupEvent(memberAuthVerifyResBody.anonymousGroup.id))
        memberAuthVerifyResBody.authenticatedMemberInfo.token
    }

    suspend fun verifyMemberAuth(connectionSettingsId: Long, anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/verify")
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
                setAGIsMemberFalse(anonymousGroupId)
                throw AGMemberUnauthorizedException()
            } catch (e: RemoteAGNotFoundException) {
                setAGExistsRemoteFalse(anonymousGroupId)
                throw e
            }
        }

    suspend fun leaveAllAG(connectionSettingsId: Long) {
        val ags = listLocalAnonymousGroups(connectionSettingsId)
        ags.forEach {
            leaveAnonymousGroup(
                anonymousGroupId = it.id, connectionSettingsId = connectionSettingsId
            )
        }
    }

    suspend fun getRemoteAGMembers(
        connectionSettingsId: Long, anonymousGroupId: String
    ): List<AGMember> = withContext(Dispatchers.IO) {
        val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
            ?: throw RemoteAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
        val response = httpClient.get {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members")
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
            setAGIsMemberFalse(anonymousGroupId)
            throw AGMemberUnauthorizedException()
        } catch (e: RemoteAGNotFoundException) {
            setAGExistsRemoteFalse(anonymousGroupId)
            throw e
        }
        val encryptedMembers = response.body<AGGetMembersResponseDto>().members
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
            anonymousGroupId = anonymousGroupId, agMemberEntities = decryptedMembers.map {
                AGMemberMapper.domainToEntity(agMember = it, anonymousGroupId = anonymousGroupId)
            })
        decryptedMembers
    }

    suspend fun verifyAdminAuth(connectionSettingsId: Long, anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            if (anonymousGroup.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/admin/auth/verify")
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
                setAGExistsRemoteFalse(anonymousGroupId)
                throw e
            } catch (e: UnauthorizedException) {
                throw AGAdminTokenInvalidException()
            }
        }

    suspend fun getAdminToken(
        connectionSettingsId: Long, anonymousGroupId: String, adminPassword: String
    ) = withContext(Dispatchers.IO) {
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        val response = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/admin/auth/token")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(AGAdminAuthRequestDto(adminPassword = adminPassword))
        }
        try {
            when (response.status.value) {
                201 -> Unit
                401, 403 -> ErrorHandler.handleUnauthorized(response)
                404 -> throw RemoteAGNotFoundException()
                else -> ErrorHandler.handleGeneric(response)
            }
        } catch (e: RemoteAGNotFoundException) {
            setAGExistsRemoteFalse(anonymousGroupId)
            throw e
        } catch (e: UnauthorizedException) {
            throw AGAdminUnauthorizedException()
        }
        val responseBody = response.body<AGAdminAuthResponseDto>()
        val encryptedAdminToken = keyStoreService.encrypt(Base64.decode(responseBody.adminToken))
        anonymousGroupDao.setAGAdminToken(
            anonymousGroupId = anonymousGroupId,
            adminTokenCipher = encryptedAdminToken.cipherText,
            adminTokenIv = encryptedAdminToken.iv,
        )
    }

    private suspend fun getAvailableConnections(
        connectionSettingsList: List<ConnectionSettings>,
    ): List<ConnectionSettings> = coroutineScope {
        connectionSettingsList.map { settings ->
            async {
                try {
                    connectionSettingsService.isApiAvailable(settings.url)
                    settings
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun getAvailableAGs(availableConnections: List<ConnectionSettings>): Int {
        var availableAGs = 0
        availableConnections.forEach {
            availableAGs += anonymousGroupDao.listAGToSendLocationOfConnection(it.id!!).size
        }
        return availableAGs
    }

    @OptIn(ExperimentalTime::class)
    suspend fun sendLocation(updateActiveAGCount: (count: Int) -> Unit) =
        withContext(Dispatchers.IO) {
            while (true) {
                var connectionSettingsList = connectionSettingsService.listConnectionSettings()
                var availableConnections = getAvailableConnections(connectionSettingsList)
                var availableAGs = getAvailableAGs(availableConnections)
                updateActiveAGCount(availableAGs)
                Log.i("", "Available connections: ${availableConnections.size}")
                Log.i("", "Available AGs: $availableAGs")
                if (availableAGs == 0) {
                    delay(10000L)
                    continue
                }
                try {
                    locationService.getLocationUpdates().collect { location ->
                        Log.d("", "Getting location updates...")
                        connectionSettingsList = connectionSettingsService.listConnectionSettings()
                        availableConnections = getAvailableConnections(connectionSettingsList)
                        availableAGs = getAvailableAGs(availableConnections)
                        updateActiveAGCount(availableAGs)
                        Log.i("", "Available connections: ${availableConnections.size}")
                        Log.i("", "Available AGs: $availableAGs")
                        if (availableAGs == 0) {
                            throw Exception("No available anonymous groups, exiting getLocationUpdates()")
                        }
                        availableConnections.map { connectionSettings ->
                            async {
                                val agsToSendLocation =
                                    anonymousGroupDao.listAGToSendLocationOfConnection(
                                        connectionSettings.id!!
                                    ).map {
                                        AnonymousGroupMapper.toDomain(
                                            entity = it, keyStoreService = keyStoreService
                                        )
                                    }

                                val locationBytes = location.toByteArray()

                                agsToSendLocation.forEach { anonymousGroup ->
                                    Log.i(
                                        "", "[ ${
                                            Clock.System.now().toLocalDateTime(TimeZone.UTC)
                                        } ] Sharing location agId: ${anonymousGroup.id}, location: $location"
                                    )
                                    try {
                                        val encryptedLocation = cryptoService.encrypt(
                                            data = locationBytes, key = anonymousGroup.key
                                        )
                                        val response = httpClient.post {
                                            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroup.id}/locations")
                                            headers {
                                                connectionSettings.apiKey?.let { ak ->
                                                    append(
                                                        "X-API-KEY", ak
                                                    )
                                                }
                                                append(
                                                    HttpHeaders.Authorization,
                                                    "AGMember ${anonymousGroup.memberId} ${
                                                        Base64.encode(
                                                            anonymousGroup.memberToken
                                                        )
                                                    }"
                                                )
                                                append(HttpHeaders.ContentType, "application/json")
                                            }
                                            setBody(
                                                AGLocationSaveRequestDto(
                                                    encryptedLocation = EncryptedDataMapper.toDto(
                                                        encryptedLocation
                                                    )
                                                )
                                            )
                                        }
                                        when (response.status.value) {
                                            201 -> Unit
                                            401, 403 -> ErrorHandler.handleUnauthorized(response)
                                            404 -> throw RemoteAGNotFoundException()
                                            else -> ErrorHandler.handleGeneric(response)
                                        }
                                    } catch (e: Exception) {
                                        when (e) {
                                            is UnauthorizedException -> setAGIsMemberFalse(
                                                anonymousGroup.id
                                            )

                                            is RemoteAGNotFoundException -> setAGExistsRemoteFalse(
                                                anonymousGroup.id
                                            )
                                        }
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    Log.d("", "Stopped getting location updates...")
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(1000L)
                }
            }
        }

    @OptIn(ExperimentalTime::class)
    suspend fun streamLocations(
        connectionSettingsId: Long, anonymousGroupId: String
    ): Flow<AGLocationUpdate> = withContext(Dispatchers.IO) {
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
        val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
            ?: throw LocalAGNotFoundException()
        val anonymousGroup = AnonymousGroupMapper.toDomain(
            entity = agEntity, keyStoreService = keyStoreService
        )
        callbackFlow {
            httpClient.sse(
                "${connectionSettings.url}/api/anonymous-groups/$anonymousGroupId/locations",
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
                                val agLocationUpdate = deserialize<AGLocationUpdateDto>(event.data)

                                if (agLocationUpdate != null) {
                                    val agLocation = AGLocationUpdateMapper.toDomain(
                                        agLocationUpdateDto = agLocationUpdate,
                                        cryptoService = cryptoService,
                                        key = anonymousGroup.key
                                    )
                                    Log.d(
                                        "", "Received location $agLocation"
                                    )
                                    anonymousGroupDao.setAGMemberLastLocation(
                                        agMemberId = agLocation.agMemberId,
                                        lastLatitude = agLocation.locationRecord.coordinates.latitude,
                                        lastLongitude = agLocation.locationRecord.coordinates.longitude,
                                        lastSeen = agLocation.locationRecord.timestamp.toInstant(
                                            TimeZone.UTC
                                        ).toEpochMilliseconds()
                                    )
                                    Log.d(
                                        "",
                                        "Set last location to ${agLocation.agMemberId}: ${agLocation.locationRecord}"
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

    suspend fun deleteAnonymousGroup(connectionSettingsId: Long, anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            val anonymousGroup = AnonymousGroupMapper.toDomain(
                entity = agEntity, keyStoreService = keyStoreService
            )
            if (anonymousGroup.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.delete {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}")
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
            deleteLocalAnonymousGroup(anonymousGroupId)
        }
}
