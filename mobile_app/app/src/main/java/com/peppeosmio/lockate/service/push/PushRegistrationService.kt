package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.PushRegistrationEntity
import com.peppeosmio.lockate.data.anonymous_group.mappers.AnonymousGroupMapper
import com.peppeosmio.lockate.data.anonymous_group.remote.PushRegistrationReqDto
import com.peppeosmio.lockate.data.anonymous_group.remote.PushRegistrationResDto
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.platform_service.PushTokenSource
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

const val PUSH_REGISTRATION_ID_BYTES = 16
const val PUSH_KEY_BYTES = 32

/**
 * Tells each server where to push our notifications for each of our groups: the phone's FCM token,
 * and a registration id and AES key generated here, once per group. Run when the app starts, when
 * we join or create a group and when FCM changes the token; a failure waits for the next run.
 */
class PushRegistrationService(
    private val anonymousGroupDao: AnonymousGroupDao,
    private val connectionService: ConnectionService,
    private val keyStoreService: KeyStoreService,
    private val httpClient: HttpClient,
    private val pushTokenSource: PushTokenSource,
    private val sendLoopLog: SendLoopLog,
    private val random: SecureRandom = SecureRandom(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Registers every group now, and each group we join or create from now on. */
    fun start(events: Flow<AnonymousGroupEvent>) {
        refreshAll()
        scope.launch {
            events.filterIsInstance<AnonymousGroupEvent.NewAnonymousGroupEvent>().collect {
                register(it.anonymousGroupInternalId)
            }
        }
    }

    fun refreshAll() {
        scope.launch { registerAll() }
    }

    suspend fun registerAll() {
        val token = pushTokenSource.token() ?: return
        anonymousGroupDao.listMemberAGs().forEach { register(it, token) }
    }

    suspend fun register(anonymousGroupInternalId: Long) {
        val token = pushTokenSource.token() ?: return
        anonymousGroupDao.getAGByInternalId(anonymousGroupInternalId)?.let { register(it, token) }
    }

    private suspend fun register(entity: AnonymousGroupEntity, token: String) {
        try {
            val registration = getOrCreateRegistration(entity.internalId)
            val key =
                keyStoreService.decrypt(EncryptedData(registration.keyCipher, registration.keyIv))
            val group = AnonymousGroupMapper.toDomain(entity, keyStoreService)
            val connection = connectionService.getConnectionSettingsById(entity.connectionId)
            val response = httpClient.put {
                url(
                    "${connection.url}/api/v1/anonymous-groups/${group.id}/members/push-registration"
                )
                headers {
                    connection.apiKey?.let { append("X-API-KEY", it) }
                    append(
                        HttpHeaders.Authorization,
                        "AGMember ${group.memberId} ${Base64.encode(group.memberToken)}",
                    )
                }
                contentType(ContentType.Application.Json)
                setBody(
                    PushRegistrationReqDto(
                        fcmToken = token,
                        registrationId = Base64.encode(registration.registrationId),
                        key = Base64.encode(key),
                    )
                )
            }
            // a server without a relay sends no notifications at all
            if (response.status.value != 200 || !response.body<PushRegistrationResDto>().pushes) {
                sendLoopLog.log("push registration ${group.id} ${response.status.value} no pushes")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendLoopLog.log("push registration ${entity.id} failed ${e::class.simpleName}")
        }
    }

    private suspend fun getOrCreateRegistration(agInternalId: Long): PushRegistrationEntity {
        anonymousGroupDao.getPushRegistration(agInternalId)?.let {
            return it
        }
        var registrationId: ByteArray
        do {
            registrationId = randomBytes(PUSH_REGISTRATION_ID_BYTES)
        } while (anonymousGroupDao.getPushRegistrationById(registrationId) != null)
        val key = keyStoreService.encrypt(randomBytes(PUSH_KEY_BYTES))
        return PushRegistrationEntity(agInternalId, registrationId, key.cipherText, key.iv).also {
            anonymousGroupDao.insertPushRegistration(it)
        }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }
}
