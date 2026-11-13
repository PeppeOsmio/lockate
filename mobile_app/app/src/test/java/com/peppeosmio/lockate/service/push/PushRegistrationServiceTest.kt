package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.PushRegistrationEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.PushRegistrationReqDto
import com.peppeosmio.lockate.data.apiJson
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRegistrationServiceTest {

    private val dao = mockk<AnonymousGroupDao>(relaxUnitFun = true)
    private val connectionService = mockk<ConnectionService>()
    private val keyStore = mockk<KeyStoreService>()
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var pushes = true

    private val httpClient =
        HttpClient(
            MockEngine { request ->
                requests += request
                respond(
                    """{"pushes":$pushes}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        ) {
            install(ContentNegotiation) { json(apiJson) }
        }

    private fun group(internalId: Long, id: String) =
        AnonymousGroupEntity(
            internalId = internalId,
            id = id,
            name = "g",
            createdAt = 0,
            joinedAt = 0,
            isMember = true,
            existsRemote = true,
            sendLocation = false,
            connectionId = 1,
            memberName = "me",
            memberId = "member-$id",
            memberTokenCipher = byteArrayOf(1),
            memberTokenIv = byteArrayOf(1),
            memberIsAGAdmin = false,
            keyCipher = byteArrayOf(2),
            keyIv = byteArrayOf(2),
        )

    private fun service(token: String? = "fcm-token") =
        PushRegistrationService(
            anonymousGroupDao = dao,
            connectionService = connectionService,
            keyStoreService = keyStore,
            httpClient = httpClient,
            pushTokenSource = { token },
            sendLoopLog = SendLoopLog(File.createTempFile("send-loop", ".log")),
        )

    init {
        coEvery { connectionService.getConnectionSettingsById(1) } returns
            Connection(1, "c", "https://server", null, null, null)
        coEvery { keyStore.decrypt(any()) } answers { firstArg<EncryptedData>().cipherText }
        coEvery { keyStore.encrypt(any()) } answers { EncryptedData(firstArg(), byteArrayOf(9)) }
        coEvery { dao.getPushRegistration(any()) } returns null
        coEvery { dao.getPushRegistrationById(any()) } returns null
    }

    private fun HttpRequestData.registration() =
        apiJson.decodeFromString<PushRegistrationReqDto>((body as TextContent).text)

    @Test
    fun `each group gets a new id and key, sent with the token`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"), group(2, "g2"))
        val stored = mutableListOf<PushRegistrationEntity>()
        coEvery { dao.insertPushRegistration(capture(stored)) } returns Unit

        service().registerAll()

        assertEquals(2, requests.size)
        val first = requests[0]
        assertEquals(HttpMethod.Put, first.method)
        assertEquals(
            "https://server/api/v1/anonymous-groups/g1/members/push-registration",
            first.url.toString(),
        )
        assertTrue(first.headers["Authorization"]!!.startsWith("AGMember member-g1 "))
        val body = first.registration()
        assertEquals("fcm-token", body.fcmToken)
        assertEquals(PUSH_REGISTRATION_ID_BYTES, Base64.decode(body.registrationId).size)
        assertEquals(PUSH_KEY_BYTES, Base64.decode(body.key).size)
        assertTrue(stored[0].registrationId.contentEquals(Base64.decode(body.registrationId)))
        assertTrue(stored[0].keyCipher.contentEquals(Base64.decode(body.key)))
        assertTrue(!stored[0].registrationId.contentEquals(stored[1].registrationId))
    }

    @Test
    fun `an existing registration is sent again, not replaced`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"))
        val existing =
            PushRegistrationEntity(1, ByteArray(16) { 7 }, ByteArray(32) { 8 }, byteArrayOf(9))
        coEvery { dao.getPushRegistration(1) } returns existing

        service().registerAll()

        val body = requests.single().registration()
        assertTrue(Base64.decode(body.registrationId).contentEquals(existing.registrationId))
        assertTrue(Base64.decode(body.key).contentEquals(existing.keyCipher))
        coVerify(exactly = 0) { dao.insertPushRegistration(any()) }
    }

    @Test
    fun `an id already used on this phone is drawn again`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"))
        val taken = PushRegistrationEntity(2, ByteArray(16), ByteArray(32), ByteArray(1))
        coEvery { dao.getPushRegistrationById(any()) } returnsMany listOf(taken, null)
        val stored = slot<PushRegistrationEntity>()
        coEvery { dao.insertPushRegistration(capture(stored)) } returns Unit

        service().registerAll()

        coVerify(exactly = 2) { dao.getPushRegistrationById(any()) }
        assertTrue(
            Base64.decode(requests.single().registration().registrationId)
                .contentEquals(stored.captured.registrationId)
        )
    }

    @Test
    fun `without a push token nothing is stored or sent`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"))

        service(token = null).registerAll()

        assertTrue(requests.isEmpty())
        coVerify(exactly = 0) { dao.insertPushRegistration(any()) }
    }

    @Test
    fun `a failing server doesn't stop the other groups`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"), group(2, "g2"))
        coEvery { connectionService.getConnectionSettingsById(1) } throws
            IllegalStateException("gone") andThen
            Connection(1, "c", "https://server", null, null, null)

        service().registerAll()

        assertEquals(1, requests.size)
    }

    @Test
    fun `a group is pushed only if its server says it pushes`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"))
        val service = service()

        service.registerAll()
        assertTrue(service.isPushed(1))

        pushes = false
        service.registerAll()
        assertFalse(service.isPushed(1))
    }

    @Test
    fun `a failed registration doesn't count as pushed`() = runTest {
        coEvery { dao.listAGsToHold() } returns listOf(group(1, "g1"))
        status = HttpStatusCode.InternalServerError

        val service = service()
        service.registerAll()

        assertFalse(service.isPushed(1))
    }
}
