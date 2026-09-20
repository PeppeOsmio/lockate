package com.peppeosmio.lockate.service.srp

import dev.whyoleg.cryptography.bigint.toJavaBigInteger
import dev.whyoleg.cryptography.bigint.toKotlinBigInt
import org.bouncycastle.crypto.agreement.srp.SRP6Server
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.digests.SHA256Digest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.io.encoding.Base64

class SrpClientServiceTest {

    private val srpClientService = SrpClientService()
    private val identifier = "alice"
    private val password = "hunter2"

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun newServer(verifier: BigInteger): SRP6Server {
        val server = SRP6Server()
        server.init(SRP6StandardGroups.rfc5054_2048, verifier, SHA256Digest(), SecureRandom())
        return server
    }

    @Test
    fun `generateVerifier is deterministic for the same identifier, password and salt`() {
        val salt = newSalt()

        val verifier1 = srpClientService.generateVerifier(identifier, password, salt)
        val verifier2 = srpClientService.generateVerifier(identifier, password, salt)

        assertEquals(verifier1, verifier2)
    }

    @Test
    fun `full handshake round trip - server accepts the client evidence message`() {
        val salt = newSalt()
        val verifier = srpClientService.generateVerifier(identifier, password, salt)
        val server = newServer(verifier)
        val serverB = server.generateServerCredentials()

        val client = srpClientService.getSrpClient()
        val clientA = srpClientService.getA(client, Base64.encode(salt), identifier, password)
        server.calculateSecret(clientA.toJavaBigInteger())
        val clientM1 = srpClientService.getM1(client, serverB.toKotlinBigInt())

        // true when the server independently agrees on the same shared secret/evidence
        assertTrue(server.verifyClientEvidenceMessage(clientM1.toJavaBigInteger()))
    }

    @Test
    fun `handshake with the wrong password fails server verification`() {
        val salt = newSalt()
        val verifier = srpClientService.generateVerifier(identifier, password, salt)
        val server = newServer(verifier)
        val serverB = server.generateServerCredentials()

        val client = srpClientService.getSrpClient()
        val clientA = srpClientService.getA(
            client, Base64.encode(salt), identifier, "wrong-password"
        )
        server.calculateSecret(clientA.toJavaBigInteger())
        val clientM1 = srpClientService.getM1(client, serverB.toKotlinBigInt())

        assertFalse(server.verifyClientEvidenceMessage(clientM1.toJavaBigInteger()))
    }
}
