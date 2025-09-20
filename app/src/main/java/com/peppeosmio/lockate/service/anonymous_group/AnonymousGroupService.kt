package com.peppeosmio.lockate.service.anonymous_group

import android.util.Log
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGAdminAuthResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGCreateResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMemberPasswordSrpInfoResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGGetMembersResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGLocationSaveRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthStartResponseDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberAuthVerifyRequestDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGLocationUpdateDto
import com.peppeosmio.lockate.domain.crypto.EncryptedStringDto
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
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
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.location.LocationService
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
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AnonymousGroupService(
    private val anonymousGroupDao: AnonymousGroupDao,
    private val cryptoService: CryptoService,
    private val connectionSettingsService: ConnectionSettingsService,
    private val httpClient: HttpClient,
    private val srpClientService: SrpClientService,
    private val locationService: LocationService
) {
    private val _events = MutableSharedFlow<AnonymousGroupEvent>()
    val events = _events.asSharedFlow()

    suspend fun listLocalAnonymousGroups(connectionSettingsId: Long): List<AnonymousGroup> =
        withContext(Dispatchers.IO) {
            val entities = anonymousGroupDao.listAnonymousGroupsOfConnection(connectionSettingsId)
            entities.map {
                AnonymousGroup.fromEntity(it)
            }
        }

    suspend fun getAnonymousGroupById(anonymousGroupId: String): AnonymousGroup =
        withContext(Dispatchers.IO) {
            val entity = anonymousGroupDao.getAnonymousGroupById(
                anonymousGroupId
            ) ?: throw LocalAGNotFoundException()
            AnonymousGroup.fromEntity(entity)
        }

    suspend fun getLocalAGMembers(anonymousGroupId: String): List<AGMember> =
        withContext(Dispatchers.IO) {
            anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            anonymousGroupDao.listAGMembers(anonymousGroupId).map {
                Log.d("", "found entity: $it")
                AGMember.fromEntity(it)
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

            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val response = httpClient.post {
                url("${connectionSettings.url}/api/anonymous-groups/$anonymousGroupId/members/leave")
                headers {
                    connectionSettings.apiKey?.let { ak -> append(name = "X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(agEntity.memberToken)}"
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
        val encryptedMemberName = cryptoService.aesGcmEncrypt(
            plainTextBytes = memberName.toByteArray(Charsets.UTF_8),
            saltBytes = cryptoService.getSalt(),
            passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
        )
        val encryptedMemberNameDto = EncryptedStringDto.fromEncryptedString(encryptedMemberName)
        val memberSrpSalt = cryptoService.getSalt()
        val memberSrpVerifier = srpClientService.generateVerifier(
            identifier = groupName, password = memberPassword, salt = memberSrpSalt
        )
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
        val encryptedGroupNameDto = EncryptedStringDto.fromEncryptedString(
            cryptoService.aesGcmEncrypt(
                plainTextBytes = groupName.toByteArray(Charsets.UTF_8),
                saltBytes = cryptoService.getSalt(),
                passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
            )
        )

        val createResponse = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/")
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
                )
            )
        }

        when (createResponse.status.value) {
            201 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(createResponse)
            else -> ErrorHandler.handleGeneric(createResponse)
        }

        val createResponseBody = createResponse.body<AGCreateResponseDto>()
        val agMemberEntity = createResponseBody.authenticatedMemberInfo.member.toDecrypted(
            cryptoService = cryptoService, memberPassword = memberPassword
        ).toEntity(
            anonymousGroupId = createResponseBody.anonymousGroup.id,
        )
        anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupEntity(
                id = createResponseBody.anonymousGroup.id,
                name = cryptoService.aesGcmDecrypt(
                    encryptedString = createResponseBody.anonymousGroup.encryptedName.toEncryptedString(),
                    passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
                ).toString(Charsets.UTF_8),
                createdAt = createResponseBody.anonymousGroup.createdAt.toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds(),
                joinedAt = agMemberEntity.createdAt,
                memberName = cryptoService.aesGcmDecrypt(
                    encryptedString = createResponseBody.authenticatedMemberInfo.member.encryptedName.toEncryptedString(),
                    passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
                ).toString(Charsets.UTF_8),
                memberId = agMemberEntity.id,
                memberToken = Base64.decode(createResponseBody.authenticatedMemberInfo.token),
                adminToken = null,
                isMember = true,
                existsRemote = true,
                memberPassword = memberPassword,
                sendLocation = true,
                connectionSettingsId = connectionSettingsId
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
            memberToken = createResponseBody.authenticatedMemberInfo.token,
            adminToken = null,
            isMember = true,
            existsRemote = true,
            memberPassword = memberPassword,
            sendLocation = true
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
            agGetMemberPasswordSrpInfoResponse.body<AGGetMemberPasswordSrpInfoResponseDto>()
        val agName: String
        try {
            agName = cryptoService.aesGcmDecrypt(
                encryptedString = agGetMemberPasswordSrpInfoResponseBody.encryptedName.toEncryptedString(),
                passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
            ).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.d("", "Anonymous group encrypted name decryption failed.")
            throw UnauthorizedException()
        }
        val A = srpClientService.getA(
            srpClient = srpClient,
            salt = agGetMemberPasswordSrpInfoResponseBody.salt,
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
        val encryptedMemberName = cryptoService.aesGcmEncrypt(
            plainTextBytes = memberName.toByteArray(Charsets.UTF_8),
            saltBytes = cryptoService.getSalt(),
            passwordBytes = memberPassword.toByteArray(Charsets.UTF_8),
        )
        val memberAuthVerifyResponse = httpClient.post {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/srp/verify")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody<AGMemberAuthVerifyRequestDto>(
                AGMemberAuthVerifyRequestDto(
                    encryptedUserName = EncryptedStringDto.fromEncryptedString(encryptedMemberName),
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
        val memberAuthVerifyResponseBody =
            memberAuthVerifyResponse.body<AGMemberAuthVerifyResponseDto>()
        val agMemberEntity =
            memberAuthVerifyResponseBody.authenticatedMemberInfo.member.toDecrypted(
                cryptoService = cryptoService, memberPassword = memberPassword
            ).toEntity(
                anonymousGroupId = anonymousGroupId
            )
        anonymousGroupDao.createAGWithMembers(
            agEntity = AnonymousGroupEntity(
                id = memberAuthVerifyResponseBody.anonymousGroup.id,
                name = cryptoService.aesGcmDecrypt(
                    encryptedString = memberAuthVerifyResponseBody.anonymousGroup.encryptedName.toEncryptedString(),
                    passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
                ).toString(Charsets.UTF_8),
                createdAt = memberAuthVerifyResponseBody.anonymousGroup.createdAt.toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds(),
                joinedAt = agMemberEntity.createdAt,
                memberName = cryptoService.aesGcmDecrypt(
                    encryptedString = memberAuthVerifyResponseBody.authenticatedMemberInfo.member.encryptedName.toEncryptedString(),
                    passwordBytes = memberPassword.toByteArray(Charsets.UTF_8)
                ).toString(Charsets.UTF_8),
                memberId = agMemberEntity.id,
                memberToken = Base64.decode(memberAuthVerifyResponseBody.authenticatedMemberInfo.token),
                adminToken = null,
                isMember = true,
                existsRemote = true,
                memberPassword = memberPassword,
                sendLocation = true,
                connectionSettingsId = connectionSettingsId
            ), agMemberEntities = listOf(agMemberEntity)
        )
        _events.emit(AnonymousGroupEvent.NewAnonymousGroupEvent(memberAuthVerifyResponseBody.anonymousGroup.id))
        memberAuthVerifyResponseBody.authenticatedMemberInfo.token
    }

    suspend fun verifyMemberAuth(connectionSettingsId: Long, anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members/auth/verify")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${agEntity.memberId} ${Base64.encode(agEntity.memberToken)}"
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
        val connectionSettings =
            connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
        val response = httpClient.get {
            url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/members")
            headers {
                connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                append(
                    HttpHeaders.Authorization,
                    "AGMember ${agEntity.memberId} ${Base64.encode(agEntity.memberToken)}"
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
                it.toDecrypted(
                    cryptoService = cryptoService, memberPassword = agEntity.memberPassword
                )
            }
        }.awaitAll()
        Log.d("", "Decrypted members: ${decryptedMembers.map { it.id }}")
        anonymousGroupDao.setAGMembers(
            anonymousGroupId = anonymousGroupId, agMemberEntities = decryptedMembers.map {
                val entity = it.toEntity(
                    anonymousGroupId = anonymousGroupId
                )
                entity
            })
        decryptedMembers
    }

    suspend fun verifyAdminAuth(connectionSettingsId: Long, anonymousGroupId: String) =
        withContext(Dispatchers.IO) {
            val connectionSettings =
                connectionSettingsService.getConnectionSettingsById(connectionSettingsId)
            val agEntity = anonymousGroupDao.getAnonymousGroupById(anonymousGroupId)
                ?: throw LocalAGNotFoundException()
            if (agEntity.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.get {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}/admin/auth/verify")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization, "AGAdmin ${Base64.encode(agEntity.adminToken)}"
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
        anonymousGroupDao.setAGAdminToken(
            anonymousGroupId = anonymousGroupId, adminToken = Base64.decode(responseBody.adminToken)
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun sendLocation(onUpdate: ((activeAGCount: Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            var count = 0
            connectionSettingsService.listConnectionSettings().forEach {
                count += anonymousGroupDao.listAGToSendLocationOfConnection(it.id!!).size
            }

            if (onUpdate != null) {
                onUpdate(count)
            }
            try {
                locationService.getLocationUpdates().collect { location ->
                    val connectionSettingsList = connectionSettingsService.listConnectionSettings()
                    connectionSettingsList.forEach { connectionSettings ->
                        val agsToSendLocation =
                            anonymousGroupDao.listAGToSendLocationOfConnection(connectionSettings.id!!)
                                .map { AnonymousGroup.fromEntity(it) }

                        if (onUpdate != null) {
                            onUpdate(agsToSendLocation.size)
                        }

                        val locationBytes = location.toByteArray()

                        agsToSendLocation.forEach { anonymousGroup ->
                            Log.i(
                                "", "[ ${
                                    Clock.System.now()
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                } ] agId: ${anonymousGroup.id}, sending location: $location"
                            )
                            launch(Dispatchers.Default) {
                                val result = ErrorHandler.runAndHandleException {
                                    val encryptedLocation = cryptoService.aesGcmEncrypt(
                                        plainTextBytes = locationBytes,
                                        saltBytes = cryptoService.getSalt(),
                                        passwordBytes = anonymousGroup.memberPassword.toByteArray(
                                            StandardCharsets.UTF_8
                                        ),
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
                                                "AGMember ${anonymousGroup.memberId} ${anonymousGroup.memberToken}"
                                            )
                                            append(HttpHeaders.ContentType, "application/json")
                                        }
                                        setBody(
                                            AGLocationSaveRequestDto(
                                                encryptedLocation = EncryptedStringDto.fromEncryptedString(
                                                    encryptedLocation
                                                )
                                            )
                                        )
                                    }
                                    try {
                                        when (response.status.value) {
                                            201 -> Unit
                                            401, 403 -> ErrorHandler.handleUnauthorized(response)
                                            404 -> throw RemoteAGNotFoundException()
                                            else -> ErrorHandler.handleGeneric(response)
                                        }
                                    } catch (e: UnauthorizedException) {
                                        setAGIsMemberFalse(anonymousGroup.id)
                                        throw AGMemberUnauthorizedException()
                                    } catch (e: RemoteAGNotFoundException) {
                                        setAGExistsRemoteFalse(anonymousGroup.id)
                                        throw e
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
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
        callbackFlow {
            try {
                httpClient.sse(
                    "${connectionSettings.url}/api/anonymous-groups/$anonymousGroupId/locations",
                    request = {
                        headers {
                            connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                            append(
                                HttpHeaders.Authorization,
                                "AGMember ${agEntity.memberId} ${Base64.encode(agEntity.memberToken)}"
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
                                    val locationUpdate =
                                        deserialize<AGLocationUpdateDto>(event.data)

                                    if (locationUpdate != null) {
                                        val decryptedUpdate = locationUpdate.toAGLocationUpdate(
                                            cryptoService = cryptoService,
                                            memberPassword = agEntity.memberPassword
                                        )
                                        Log.d(
                                            "", decryptedUpdate.toString()
                                        )
                                        anonymousGroupDao.setAGMemberLastLocation(
                                            agMemberId = decryptedUpdate.agMemberId,
                                            lastLocationLatitude = decryptedUpdate.location.coordinates.latitude,
                                            lastLocationLongitude = decryptedUpdate.location.coordinates.longitude,
                                            lastLocationTimestamp = decryptedUpdate.location.timestamp.toInstant(
                                                TimeZone.currentSystemDefault()
                                            ).toEpochMilliseconds()
                                        )
                                        Log.d(
                                            "",
                                            "Set last location to ${decryptedUpdate.agMemberId}: ${decryptedUpdate.location}"
                                        )
                                        send(
                                            decryptedUpdate
                                        )
                                    }
                                }

                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
            if (agEntity.adminToken == null) {
                throw AGAdminTokenInvalidException()
            }
            val response = httpClient.delete {
                url("${connectionSettings.url}/api/anonymous-groups/${anonymousGroupId}")
                headers {
                    connectionSettings.apiKey?.let { ak -> append("X-API-KEY", ak) }
                    append(
                        HttpHeaders.Authorization, "AGAdmin ${Base64.encode(agEntity.adminToken)}"
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
