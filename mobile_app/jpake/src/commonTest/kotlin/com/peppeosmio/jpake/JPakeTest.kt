package com.peppeosmio.jpake

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class JPakeTest {

    private val password = "correct horse".encodeToByteArray()

    private fun ByteArray.flipMiddleBit() =
        copyOf().also { it[size / 2] = (it[size / 2].toInt() xor 1).toByte() }

    /** Runs both parties through the rounds in the order the flow uses and returns their secrets */
    private fun exchange(serverPassword: ByteArray, clientPassword: ByteArray): Pair<ByteArray, ByteArray> {
        val server = JPake(JPakeRole.SERVER, serverPassword)
        val client = JPake(JPakeRole.CLIENT, clientPassword)
        val serverRound1 = server.writeRound1()
        client.readRound1(serverRound1)
        val clientRound1 = client.writeRound1()
        val clientRound2 = client.writeRound2()
        server.readRound1(clientRound1)
        val serverRound2 = server.writeRound2()
        server.readRound2(clientRound2)
        client.readRound2(serverRound2)
        return server.deriveSecret() to client.deriveSecret()
    }

    @Test
    fun bothSidesGetTheSameSecretWithTheSamePassword() {
        val (serverSecret, clientSecret) = exchange(password, password)
        assertEquals(32, serverSecret.size)
        assertContentEquals(serverSecret, clientSecret)
    }

    @Test
    fun theSecretsDifferWithDifferentPasswords() {
        val (serverSecret, clientSecret) = exchange(password, "correct horsE".encodeToByteArray())
        assertFalse(serverSecret.contentEquals(clientSecret))
    }

    @Test
    fun everyExchangeGivesAFreshSecret() {
        assertFalse(exchange(password, password).first.contentEquals(exchange(password, password).first))
    }

    @Test
    fun roundOneExposesTheTwoKeySharesAsUncompressedPoints() {
        val round1 = JPake(JPakeRole.SERVER, password).writeRound1()
        val shares = round1.keyShares()
        assertEquals(2, shares.size)
        assertEquals(listOf(65, 65), shares.map { it.size })
        assertEquals(listOf(4, 4), shares.map { it[0].toInt() })
    }

    @Test
    fun tamperedRoundsAreRejected() {
        val server = JPake(JPakeRole.SERVER, password)
        val client = JPake(JPakeRole.CLIENT, password)
        val serverRound1 = server.writeRound1()
        assertFailsWith<JPakeException> {
            client.readRound1(JPakeRound1(serverRound1.bytes.flipMiddleBit()))
        }

        val client2 = JPake(JPakeRole.CLIENT, password)
        client2.readRound1(serverRound1)
        val clientRound1 = client2.writeRound1()
        val clientRound2 = client2.writeRound2()
        val server2 = JPake(JPakeRole.SERVER, password)
        server2.writeRound1()
        server2.readRound1(clientRound1)
        server2.writeRound2()
        assertFailsWith<JPakeException> {
            server2.readRound2(JPakeRound2(clientRound2.bytes.flipMiddleBit()))
        }
    }

    @Test
    fun malformedRoundsAreRejected() {
        val round1 = JPake(JPakeRole.SERVER, password).writeRound1()
        assertFailsWith<JPakeException> {
            JPake(JPakeRole.CLIENT, password).readRound1(JPakeRound1(ByteArray(0)))
        }
        assertFailsWith<JPakeException> {
            JPake(JPakeRole.CLIENT, password).readRound1(JPakeRound1(round1.bytes.copyOf(40)))
        }
        assertFailsWith<JPakeException> { JPakeRound1(byteArrayOf(1, 2, 3)).keyShares() }
    }

    @Test
    fun aFailedExchangeCannotBeContinued() {
        val client = JPake(JPakeRole.CLIENT, password)
        assertFailsWith<JPakeException> { client.readRound1(JPakeRound1(ByteArray(0))) }
        assertFailsWith<JPakeException> { client.writeRound1() }
    }

    @Test
    fun closeIsSafeToCallTwice() {
        val jpake = JPake(JPakeRole.SERVER, password)
        jpake.close()
        jpake.close()
    }
}
