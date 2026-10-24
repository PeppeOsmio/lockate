package com.peppeosmio.lockate.service.invite

import com.peppeosmio.jpake.ClientConfirm
import com.peppeosmio.jpake.ClientReply
import com.peppeosmio.jpake.JPakeClient
import com.peppeosmio.jpake.JPakeConfig
import com.peppeosmio.jpake.JPakeException
import com.peppeosmio.jpake.JPakeRound1
import com.peppeosmio.jpake.JPakeRound2
import com.peppeosmio.jpake.JPakeServer
import com.peppeosmio.jpake.JPakeSession
import com.peppeosmio.jpake.SealedMessage
import com.peppeosmio.jpake.ServerConfirm
import com.peppeosmio.jpake.ServerHello
import com.peppeosmio.jpake.WordPassphrase
import com.peppeosmio.jpake.lvCat
import com.peppeosmio.jpake.lvSplit
import dev.whyoleg.cryptography.random.CryptographyRandom

/*
 * The Lockate invite exchange ("lockate-invite-v2") on top of the generic `jpake` library: J-PAKE
 * with the words of the invite code as the password, relayed by the server as opaque blobs. The
 * inviter is the J-PAKE server.
 * 1. inviter -> invitee: InviterHello (sid, round 1)
 * 2. invitee -> inviter: InviteeReply (round 1, round 2)
 * 3. inviter -> invitee: InviterConfirmation (round 2, Ta)
 * 4. invitee -> inviter: Tb
 * 5. inviter -> invitee: InviterFinal (the payload, encrypted with a key derived from the J-PAKE key)
 * The tags prove the keys match before anything is sent: the inviter checks Tb before the payload
 * leaves the phone.
 */

/** The session id and the inviter's J-PAKE round 1 */
class InviterHello(val sid: ByteArray, val round1: ByteArray)

/** The invitee's J-PAKE rounds 1 and 2 */
class InviteeReply(val round1: ByteArray, val round2: ByteArray)

/** The inviter's J-PAKE round 2 and its key confirmation tag */
class InviterConfirmation(val round2: ByteArray, val ta: ByteArray)

/** The invitee's key confirmation tag. [inviterTagValid] is false if the codes didn't match. */
class InviteeConfirmation(val tb: ByteArray, val inviterTagValid: Boolean)

/** The group credentials, encrypted for the invitee */
class InviterFinal(val payloadIv: ByteArray, val payloadCipherText: ByteArray)

class InvitePayload(val groupId: String, val groupKey: ByteArray, val joinToken: ByteArray)

/** The two sides used different codes, or a message was tampered with */
class InviteAbortedException : Exception("Invite exchange aborted")

private const val SID_BYTES = 16
private val PASSWORD_LABEL = "lockate-invite-v2".encodeToByteArray()
private val PAYLOAD_INFO = "lockate-invite-payload".encodeToByteArray()

private fun password(words: List<String>) = lvCat(PASSWORD_LABEL, WordPassphrase.bytes(words))

/** The session id goes into every key and tag, so a message from another invite never verifies */
private fun config(sid: ByteArray) = JPakeConfig(
    context = sid,
    serverId = "lockate-inviter".encodeToByteArray(),
    clientId = "lockate-invitee".encodeToByteArray()
)

private inline fun <T> aborting(block: () -> T): T = try {
    block()
} catch (_: JPakeException) {
    throw InviteAbortedException()
}

class Inviter private constructor(
    private val server: JPakeServer, val hello: InviterHello
) {
    companion object {
        /** Takes only the words because the server assigns the nameplate after seeing the hello */
        fun start(words: List<String>): Inviter {
            val sid = CryptographyRandom.nextBytes(SID_BYTES)
            val server = JPakeServer.start(password(words), config(sid))
            return Inviter(server, InviterHello(sid, server.hello.round1.bytes))
        }
    }

    /** For an invite that is dropped before [confirm] completes */
    fun close() = server.close()

    /** Throws [InviteAbortedException] if the invitee's rounds aren't valid J-PAKE messages */
    suspend fun confirm(reply: InviteeReply): InviterConfirmation = aborting {
        val confirm = server.respond(ClientReply(JPakeRound1(reply.round1), JPakeRound2(reply.round2)))
        InviterConfirmation(confirm.round2.bytes, confirm.tag)
    }

    /** Throws [InviteAbortedException] when the invitee didn't use the same code */
    suspend fun finish(tb: ByteArray, payload: InvitePayload): InviterFinal = aborting {
        val sealed = server.finish(ClientConfirm(tb)).seal(
            lvCat(payload.groupId.encodeToByteArray(), payload.groupKey, payload.joinToken),
            PAYLOAD_INFO
        )
        InviterFinal(payloadIv = sealed.iv, payloadCipherText = sealed.cipherText)
    }
}

class Invitee private constructor(
    private val client: JPakeClient, val reply: InviteeReply
) {
    private var session: JPakeSession? = null

    companion object {
        /** Throws [InviteAbortedException] when the inviter's message is malformed or invalid */
        fun respond(code: InviteCode, hello: InviterHello): Invitee = aborting {
            if (hello.sid.size != SID_BYTES) throw InviteAbortedException()
            val client = JPakeClient.respond(
                password(code.words), ServerHello(JPakeRound1(hello.round1)), config(hello.sid)
            )
            Invitee(client, InviteeReply(client.reply.round1.bytes, client.reply.round2.bytes))
        }
    }

    /** For an invite that is dropped before [confirm] completes */
    fun close() = client.close()

    /**
     * Throws [InviteAbortedException] if the inviter's round isn't a valid J-PAKE message. The tag
     * is returned even when the inviter's didn't check out, so the inviter learns about the
     * mismatch at once instead of waiting for a timeout.
     */
    suspend fun confirm(confirmation: InviterConfirmation): InviteeConfirmation = aborting {
        val result = client.confirm(ServerConfirm(JPakeRound2(confirmation.round2), confirmation.ta))
        if (result.serverVerified) session = client.session()
        InviteeConfirmation(result.confirm.tag, result.serverVerified)
    }

    /** Throws [InviteAbortedException] when the inviter didn't use the same code or the payload was altered */
    suspend fun open(final: InviterFinal): InvitePayload = aborting {
        val session = this.session ?: throw InviteAbortedException()
        val plaintext = session.open(SealedMessage(final.payloadIv, final.payloadCipherText), PAYLOAD_INFO)
        val parts = lvSplit(plaintext)?.takeIf { it.size == 3 } ?: throw InviteAbortedException()
        InvitePayload(groupId = parts[0].decodeToString(), groupKey = parts[1], joinToken = parts[2])
    }
}
