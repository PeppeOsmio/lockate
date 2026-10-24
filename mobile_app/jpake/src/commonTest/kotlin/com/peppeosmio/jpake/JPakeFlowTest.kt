package com.peppeosmio.jpake

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JPakeFlowTest {

    private val password = "acid-acorn-acre".encodeToByteArray()
    private val message = "the group key".encodeToByteArray()
    private val info = "test-message".encodeToByteArray()

    private fun ByteArray.flipFirstBit() = copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

    private fun ByteArray.flipMiddleBit() =
        copyOf().also { it[size / 2] = (it[size / 2].toInt() xor 1).toByte() }

    @Test
    fun serverAndClientEndUpWithASessionThatSealsAndOpensMessages() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        val serverConfirm = server.respond(client.reply)
        val clientResult = client.confirm(serverConfirm)
        assertTrue(clientResult.serverVerified)
        val serverSession = server.finish(clientResult.confirm)
        val clientSession = client.session()

        val sealed = serverSession.seal(message, info)

        assertContentEquals(message, clientSession.open(sealed, info))
        assertContentEquals(message, serverSession.open(clientSession.seal(message, info), info))
    }

    @Test
    fun theServerRefusesTheClientWhenThePasswordIsWrong() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond("acid-acorn-acted".encodeToByteArray(), server.hello)
        val clientResult = client.confirm(server.respond(client.reply))

        assertFailsWith<JPakeException> { server.finish(clientResult.confirm) }
    }

    @Test
    fun theClientNoticesAWrongPasswordAndStillAnswersWithItsTag() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond("acid-acorn-acted".encodeToByteArray(), server.hello)
        val clientResult = client.confirm(server.respond(client.reply))

        assertFalse(clientResult.serverVerified)
        assertEquals(32, clientResult.confirm.tag.size)
        assertFailsWith<JPakeException> { client.session() }
    }

    @Test
    fun aDifferentConfigOnOneSideFailsTheConfirmation() = runTest {
        val server = JPakeServer.start(password, JPakeConfig(context = byteArrayOf(1)))
        val client = JPakeClient.respond(password, server.hello, JPakeConfig(context = byteArrayOf(2)))
        val clientResult = client.confirm(server.respond(client.reply))

        assertFalse(clientResult.serverVerified)
        assertFailsWith<JPakeException> { server.finish(clientResult.confirm) }
    }

    @Test
    fun tamperedMessagesAreRejected() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        assertFailsWith<JPakeException> {
            server.respond(
                ClientReply(
                    JPakeRound1(client.reply.round1.bytes.flipMiddleBit()), client.reply.round2
                )
            )
        }

        val server2 = JPakeServer.start(password)
        val client2 = JPakeClient.respond(password, server2.hello)
        val serverConfirm = server2.respond(client2.reply)
        assertFailsWith<JPakeException> {
            client2.confirm(
                ServerConfirm(JPakeRound2(serverConfirm.round2.bytes.flipMiddleBit()), serverConfirm.tag)
            )
        }
    }

    @Test
    fun aTamperedTagIsRejectedOnBothSides() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        val serverConfirm = server.respond(client.reply)
        val tampered = client.confirm(ServerConfirm(serverConfirm.round2, serverConfirm.tag.flipFirstBit()))
        assertFalse(tampered.serverVerified)

        val server2 = JPakeServer.start(password)
        val client2 = JPakeClient.respond(password, server2.hello)
        val clientResult = client2.confirm(server2.respond(client2.reply))
        assertFailsWith<JPakeException> {
            server2.finish(ClientConfirm(clientResult.confirm.tag.flipFirstBit()))
        }
    }

    @Test
    fun aTamperedOrMisusedSealedMessageIsRejected() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        val clientResult = client.confirm(server.respond(client.reply))
        val serverSession = server.finish(clientResult.confirm)
        val clientSession = client.session()
        val sealed = serverSession.seal(message, info)

        assertFailsWith<JPakeException> {
            clientSession.open(SealedMessage(sealed.iv, sealed.cipherText.flipFirstBit()), info)
        }
        assertFailsWith<JPakeException> {
            clientSession.open(SealedMessage(sealed.iv.flipFirstBit(), sealed.cipherText), info)
        }
        assertFailsWith<JPakeException> { clientSession.open(sealed, "other".encodeToByteArray()) }
    }

    @Test
    fun theClientCannotOpenASessionBeforeConfirming() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        assertFailsWith<JPakeException> { client.session() }
        assertFailsWith<JPakeException> { server.finish(ClientConfirm(ByteArray(32))) }
    }

    @Test
    fun theConfigChangesTheIvSizeAndTheHash() = runTest {
        val config = JPakeConfig(hash = KdfHash.SHA512, ivBytes = 16)
        val server = JPakeServer.start(password, config)
        val client = JPakeClient.respond(password, server.hello, config)
        val serverConfirm = server.respond(client.reply)
        assertEquals(64, serverConfirm.tag.size)
        val clientResult = client.confirm(serverConfirm)
        val serverSession = server.finish(clientResult.confirm)

        val sealed = serverSession.seal(message, info)

        assertEquals(16, sealed.iv.size)
        assertContentEquals(message, client.session().open(sealed, info))
    }

    @Test
    fun everySealUsesAFreshIv() = runTest {
        val server = JPakeServer.start(password)
        val client = JPakeClient.respond(password, server.hello)
        val serverSession = server.finish(client.confirm(server.respond(client.reply)).confirm)

        assertFalse(serverSession.seal(message, info).iv.contentEquals(serverSession.seal(message, info).iv))
    }
}
