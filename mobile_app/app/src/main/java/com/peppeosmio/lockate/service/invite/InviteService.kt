package com.peppeosmio.lockate.service.invite

import com.peppeosmio.lockate.data.anonymous_group.mappers.EncryptedDataMapper
import com.peppeosmio.lockate.data.invite.remote.InviteClaimDto
import com.peppeosmio.lockate.data.invite.remote.InviteCreateReqDto
import com.peppeosmio.lockate.data.invite.remote.InviteCreateResDto
import com.peppeosmio.lockate.data.invite.remote.InviteeConfirmDto
import com.peppeosmio.lockate.data.invite.remote.InviteeMessageDto
import com.peppeosmio.lockate.data.invite.remote.InviteeProgressDto
import com.peppeosmio.lockate.data.invite.remote.InviterConfirmDto
import com.peppeosmio.lockate.data.invite.remote.InviterFinalDto
import com.peppeosmio.lockate.data.invite.remote.InviterHelloDto
import com.peppeosmio.lockate.data.invite.remote.InviterMessageDto
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.exceptions.InviteAlreadyClaimedException
import com.peppeosmio.lockate.exceptions.InviteExpiredException
import com.peppeosmio.lockate.exceptions.InviteNotFoundException
import com.peppeosmio.lockate.exceptions.InviteRejectedException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.TooManyRequestsException
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Adds people to a group with an invite code, see the `jpake` module for the exchange itself. The
 * server only relays the messages: the group key and the join token travel encrypted with a key
 * that only the two phones can derive, and only if both used the same code.
 */
class InviteService(
    private val anonymousGroupService: AnonymousGroupService,
    private val connectionService: ConnectionService,
    private val httpClient: HttpClient,
) {
    companion object {
        private val POLL_INTERVAL = 1.seconds

        // How long the server keeps an invite
        private val INVITE_TTL = 10.minutes
        private const val CLAIM_TOKEN_HEADER = "X-Invite-Claim"
    }

    fun startInvite(anonymousGroup: AnonymousGroup): Flow<InviteState> = flow {
        emit(InviteState.Creating)
        val connection = connectionService.getConnectionSettingsById(anonymousGroup.connectionId)
        val words = InviteCode.generateWords()
        val inviter = Inviter.start(words)
        var invite: InviteCreateResDto? = null
        var finalSent = false
        try {
            val created = createInvite(connection, anonymousGroup, inviter.hello)
            invite = created
            val expiresAt = created.expiresAt.toInstant(TimeZone.UTC)
            emit(InviteState.WaitingForInvitee(InviteCode(created.nameplate, words), expiresAt))

            val progress = pollUntil(expiresAt) {
                getInviteeProgress(connection, anonymousGroup, created.inviteId)
                    .takeIf { it.round1 != null && it.round2 != null }
            } ?: run {
                emit(InviteState.Expired)
                return@flow
            }
            emit(InviteState.Verifying)
            val confirmation = try {
                inviter.confirm(
                    InviteeReply(
                        round1 = Base64.decode(progress.round1!!),
                        round2 = Base64.decode(progress.round2!!)
                    )
                )
            } catch (_: InviteAbortedException) {
                emit(InviteState.WrongCode)
                return@flow
            }
            postConfirmation(connection, anonymousGroup, created.inviteId, confirmation)

            val tb = pollUntil(expiresAt) {
                getInviteeProgress(connection, anonymousGroup, created.inviteId).tb
            } ?: run {
                emit(InviteState.Expired)
                return@flow
            }
            val final = try {
                inviter.finish(
                    tb = Base64.decode(tb),
                    payload = InvitePayload(
                        groupId = anonymousGroup.id,
                        groupKey = anonymousGroup.key,
                        joinToken = Base64.decode(created.joinToken)
                    )
                )
            } catch (_: InviteAbortedException) {
                emit(InviteState.WrongCode)
                return@flow
            }
            emit(InviteState.Sending)
            postFinal(connection, anonymousGroup, created.inviteId, final)
            finalSent = true
            emit(InviteState.Done)
        } finally {
            inviter.close()
            // An invite whose exchange didn't complete must not stay usable: the code was shown and
            // a wrong reply already used up its only guess
            val created = invite
            if (!finalSent && created != null) {
                withContext(NonCancellable) {
                    runCatching { cancelInvite(connection, anonymousGroup, created.inviteId) }
                }
            }
        }
    }.catch { e ->
        emit(if (e is InviteExpiredException) InviteState.Expired else InviteState.Failed(e))
    }

    @Throws(
        InviteNotFoundException::class,
        InviteAlreadyClaimedException::class,
        InviteRejectedException::class,
        InviteExpiredException::class,
        TooManyRequestsException::class,
    )
    suspend fun joinWithCode(
        connectionId: Long, code: InviteCode, memberName: String
    ): AnonymousGroup {
        val connection = connectionService.getConnectionSettingsById(connectionId)
        val hello = getInviterHello(connection, code.nameplate)
        val invitee = try {
            Invitee.respond(code, hello)
        } catch (_: InviteAbortedException) {
            throw InviteRejectedException()
        }
        try {
            val claimToken = postInviteeMessage(connection, code.nameplate, invitee.reply)
            val deadline = Clock.System.now() + INVITE_TTL

            val inviterMessage = pollUntil(deadline) {
                getInviterMessage(connection, code.nameplate, claimToken)
                    .takeIf { it.round2 != null && it.ta != null }
            } ?: throw InviteExpiredException()
            val confirmation = try {
                invitee.confirm(
                    InviterConfirmation(
                        round2 = Base64.decode(inviterMessage.round2!!),
                        ta = Base64.decode(inviterMessage.ta!!)
                    )
                )
            } catch (_: InviteAbortedException) {
                throw InviteRejectedException()
            }
            // Posted even when the inviter's tag was wrong, so the inviter finds out at once
            postInviteeConfirmation(connection, code.nameplate, claimToken, confirmation.tb)
            if (!confirmation.inviterTagValid) throw InviteRejectedException()

            val encryptedPayload = pollUntil(deadline) {
                getInviterMessage(connection, code.nameplate, claimToken).payload
            } ?: throw InviteExpiredException()
            val payload = try {
                invitee.open(
                    InviterFinal(
                        payloadIv = Base64.decode(encryptedPayload.iv),
                        payloadCipherText = Base64.decode(encryptedPayload.cipherText)
                    )
                )
            } catch (_: InviteAbortedException) {
                throw InviteRejectedException()
            }
            return anonymousGroupService.joinWithInvite(
                connectionId = connectionId,
                anonymousGroupId = payload.groupId,
                groupKey = payload.groupKey,
                joinToken = payload.joinToken,
                memberName = memberName
            )
        } finally {
            invitee.close()
        }
    }

    private suspend fun <T : Any> pollUntil(deadline: Instant, poll: suspend () -> T?): T? {
        while (Clock.System.now() < deadline) {
            poll()?.let { return it }
            delay(POLL_INTERVAL)
        }
        return null
    }

    private fun HttpRequestBuilder.commonHeaders(connection: Connection, json: Boolean = false) {
        headers {
            connection.apiKey?.let { ak -> append("X-API-KEY", ak) }
            if (json) append(HttpHeaders.ContentType, "application/json")
        }
    }

    private fun HttpRequestBuilder.memberHeaders(
        connection: Connection, anonymousGroup: AnonymousGroup, json: Boolean = false
    ) {
        commonHeaders(connection, json)
        headers {
            append(
                HttpHeaders.Authorization,
                "AGMember ${anonymousGroup.memberId} ${Base64.encode(anonymousGroup.memberToken)}"
            )
        }
    }

    private fun HttpRequestBuilder.claimHeaders(
        connection: Connection, claimToken: String, json: Boolean = false
    ) {
        commonHeaders(connection, json)
        headers { append(CLAIM_TOKEN_HEADER, claimToken) }
    }

    private suspend fun HttpResponse.checkStatus(onNotFound: () -> Exception) {
        when (status.value) {
            in 200..299 -> Unit
            401, 403 -> ErrorHandler.handleUnauthorized(this)
            404 -> throw onNotFound()
            409 -> throw InviteAlreadyClaimedException()
            429 -> throw TooManyRequestsException()
            else -> ErrorHandler.handleGeneric(this)
        }
    }

    private fun groupInvitesUrl(connection: Connection, anonymousGroup: AnonymousGroup) =
        "${connection.url}/api/anonymous-groups/${anonymousGroup.id}/invites"

    private suspend fun createInvite(
        connection: Connection, anonymousGroup: AnonymousGroup, hello: InviterHello
    ): InviteCreateResDto {
        val response = httpClient.post {
            url(groupInvitesUrl(connection, anonymousGroup))
            memberHeaders(connection, anonymousGroup, json = true)
            setBody(
                InviteCreateReqDto(
                    sid = Base64.encode(hello.sid), round1 = Base64.encode(hello.round1)
                )
            )
        }
        response.checkStatus { RemoteAGNotFoundException() }
        return response.body<InviteCreateResDto>()
    }

    private suspend fun getInviteeProgress(
        connection: Connection, anonymousGroup: AnonymousGroup, inviteId: String
    ): InviteeProgressDto {
        val response = httpClient.get {
            url("${groupInvitesUrl(connection, anonymousGroup)}/$inviteId")
            memberHeaders(connection, anonymousGroup)
        }
        response.checkStatus { InviteExpiredException() }
        return response.body<InviteeProgressDto>()
    }

    private suspend fun postConfirmation(
        connection: Connection,
        anonymousGroup: AnonymousGroup,
        inviteId: String,
        confirmation: InviterConfirmation
    ) {
        val response = httpClient.post {
            url("${groupInvitesUrl(connection, anonymousGroup)}/$inviteId/confirm")
            memberHeaders(connection, anonymousGroup, json = true)
            setBody(
                InviterConfirmDto(
                    round2 = Base64.encode(confirmation.round2), ta = Base64.encode(confirmation.ta)
                )
            )
        }
        response.checkStatus { InviteExpiredException() }
    }

    private suspend fun postFinal(
        connection: Connection, anonymousGroup: AnonymousGroup, inviteId: String, final: InviterFinal
    ) {
        val response = httpClient.post {
            url("${groupInvitesUrl(connection, anonymousGroup)}/$inviteId/final")
            memberHeaders(connection, anonymousGroup, json = true)
            setBody(
                InviterFinalDto(
                    payload = EncryptedDataMapper.toDto(
                        EncryptedData(cipherText = final.payloadCipherText, iv = final.payloadIv)
                    )
                )
            )
        }
        response.checkStatus { InviteExpiredException() }
    }

    private suspend fun cancelInvite(
        connection: Connection, anonymousGroup: AnonymousGroup, inviteId: String
    ) {
        val response = httpClient.delete {
            url("${groupInvitesUrl(connection, anonymousGroup)}/$inviteId")
            memberHeaders(connection, anonymousGroup)
        }
        response.checkStatus { InviteExpiredException() }
    }

    private suspend fun getInviterHello(connection: Connection, nameplate: Int): InviterHello {
        val response = httpClient.get {
            url("${connection.url}/api/invites/$nameplate")
            commonHeaders(connection)
        }
        response.checkStatus { InviteNotFoundException() }
        val dto = response.body<InviterHelloDto>()
        return InviterHello(sid = Base64.decode(dto.sid), round1 = Base64.decode(dto.round1))
    }

    /** @return the claim token, which proves later requests come from the one who answered */
    private suspend fun postInviteeMessage(
        connection: Connection, nameplate: Int, reply: InviteeReply
    ): String {
        val response = httpClient.post {
            url("${connection.url}/api/invites/$nameplate/invitee-message")
            commonHeaders(connection, json = true)
            setBody(
                InviteeMessageDto(
                    round1 = Base64.encode(reply.round1), round2 = Base64.encode(reply.round2)
                )
            )
        }
        response.checkStatus { InviteNotFoundException() }
        return response.body<InviteClaimDto>().claimToken
    }

    /** A 404 after the reply was accepted means the inviter cancelled: it didn't accept the words */
    private suspend fun getInviterMessage(
        connection: Connection, nameplate: Int, claimToken: String
    ): InviterMessageDto {
        val response = httpClient.get {
            url("${connection.url}/api/invites/$nameplate/inviter-message")
            claimHeaders(connection, claimToken)
        }
        response.checkStatus { InviteRejectedException() }
        return response.body<InviterMessageDto>()
    }

    private suspend fun postInviteeConfirmation(
        connection: Connection, nameplate: Int, claimToken: String, tb: ByteArray
    ) {
        val response = httpClient.post {
            url("${connection.url}/api/invites/$nameplate/confirm")
            claimHeaders(connection, claimToken, json = true)
            setBody(InviteeConfirmDto(tb = Base64.encode(tb)))
        }
        response.checkStatus { InviteRejectedException() }
    }
}
