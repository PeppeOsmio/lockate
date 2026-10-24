package com.peppeosmio.jpake

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyConfirmationTest {

    private class Parties(
        val server: ConfirmedKeys, val client: ConfirmedKeys,
        val serverRound1: JPakeRound1, val clientRound1: JPakeRound1, val secret: ByteArray
    )

    private fun parties(
        serverPassword: String = "pw",
        clientPassword: String = "pw",
        serverConfig: JPakeConfig = JPakeConfig(),
        clientConfig: JPakeConfig = serverConfig
    ): Parties {
        val server = JPake(JPakeRole.SERVER, serverPassword.encodeToByteArray())
        val client = JPake(JPakeRole.CLIENT, clientPassword.encodeToByteArray())
        val serverRound1 = server.writeRound1()
        client.readRound1(serverRound1)
        val clientRound1 = client.writeRound1()
        val clientRound2 = client.writeRound2()
        server.readRound1(clientRound1)
        val serverRound2 = server.writeRound2()
        server.readRound2(clientRound2)
        client.readRound2(serverRound2)
        val serverSecret = server.deriveSecret()
        val clientSecret = client.deriveSecret()
        return Parties(
            ConfirmedKeys(serverSecret, JPakeRole.SERVER, serverConfig, serverRound1, clientRound1),
            ConfirmedKeys(clientSecret, JPakeRole.CLIENT, clientConfig, clientRound1, serverRound1),
            serverRound1, clientRound1, serverSecret
        )
    }

    private suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val provider = CryptographyProvider.Default
        return provider.get(HMAC).keyDecoder(provider.get(SHA256).id)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
            .signatureGenerator().generateSignature(data)
    }

    @Test
    fun theServerTagIsTheRfc8236MacTag() = runTest {
        val p = parties()
        val config = JPakeConfig()
        val kcKey = CryptographyProvider.Default.get(HKDF).secretDerivation(
            digest = CryptographyProvider.Default.get(SHA256).id,
            outputSize = 256.bits, salt = config.context, info = config.confirmationLabel
        ).deriveSecret(p.secret).toByteArray()
        val (g1, g2) = p.serverRound1.keyShares()
        val (g3, g4) = p.clientRound1.keyShares()

        val expected = hmacSha256(
            kcKey,
            "KC_1_U".encodeToByteArray() + "server".encodeToByteArray() +
                "client".encodeToByteArray() + g1 + g2 + g3 + g4
        )

        assertContentEquals(expected, p.server.localTag())
    }

    @Test
    fun theClientTagMirrorsTheServerOne() = runTest {
        val p = parties()
        val kcKey = CryptographyProvider.Default.get(HKDF).secretDerivation(
            digest = CryptographyProvider.Default.get(SHA256).id,
            outputSize = 256.bits, salt = ByteArray(0), info = "JPAKE_KC".encodeToByteArray()
        ).deriveSecret(p.secret).toByteArray()
        val (g1, g2) = p.serverRound1.keyShares()
        val (g3, g4) = p.clientRound1.keyShares()

        val expected = hmacSha256(
            kcKey,
            "KC_1_U".encodeToByteArray() + "client".encodeToByteArray() +
                "server".encodeToByteArray() + g3 + g4 + g1 + g2
        )

        assertContentEquals(expected, p.client.localTag())
    }

    @Test
    fun eachSideVerifiesTheOthersTag() = runTest {
        val p = parties()
        assertTrue(p.server.verifyPeerTag(p.client.localTag()))
        assertTrue(p.client.verifyPeerTag(p.server.localTag()))
        assertFalse(p.server.localTag().contentEquals(p.client.localTag()))
        assertEquals(32, p.server.localTag().size)
    }

    @Test
    fun aTagDoesNotVerifyWithADifferentPassword() = runTest {
        val p = parties(serverPassword = "pw", clientPassword = "pW")
        assertFalse(p.server.verifyPeerTag(p.client.localTag()))
        assertFalse(p.client.verifyPeerTag(p.server.localTag()))
    }

    @Test
    fun aTagDoesNotVerifyWhenAConfigValueDiffers() = runTest {
        val base = JPakeConfig()
        val variants = listOf(
            JPakeConfig(context = byteArrayOf(1)),
            JPakeConfig(serverId = "other".encodeToByteArray()),
            JPakeConfig(clientId = "other".encodeToByteArray()),
            JPakeConfig(confirmationLabel = "other".encodeToByteArray()),
            JPakeConfig(tagLabel = "other".encodeToByteArray()),
            JPakeConfig(hash = KdfHash.SHA512),
        )
        for (variant in variants) {
            val p = parties(serverConfig = base, clientConfig = variant)
            assertFalse(p.server.verifyPeerTag(p.client.localTag()))
        }
    }

    @Test
    fun aTamperedOrWrongSizedTagIsRejected() = runTest {
        val p = parties()
        val tag = p.client.localTag()
        assertFalse(p.server.verifyPeerTag(tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertFalse(p.server.verifyPeerTag(tag.copyOf(31)))
        assertFalse(p.server.verifyPeerTag(ByteArray(0)))
    }

    @Test
    fun sha512ConfigGivesLongerTags() = runTest {
        val p = parties(serverConfig = JPakeConfig(hash = KdfHash.SHA512))
        assertEquals(64, p.server.localTag().size)
        assertTrue(p.server.verifyPeerTag(p.client.localTag()))
    }

    @Test
    fun derivedKeysAreIndependentPerInfoAndMatchOnBothSides() = runTest {
        val p = parties()
        val a = p.server.deriveKey("a".encodeToByteArray())
        assertContentEquals(a, p.client.deriveKey("a".encodeToByteArray()))
        assertFalse(a.contentEquals(p.server.deriveKey("b".encodeToByteArray())))
        assertEquals(32, a.size)
        assertEquals(16, p.server.deriveKey("a".encodeToByteArray(), bits = 128).size)
    }
}
