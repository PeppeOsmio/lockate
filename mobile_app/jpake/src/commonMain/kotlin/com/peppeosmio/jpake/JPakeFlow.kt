package com.peppeosmio.jpake

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom

/*
 * The ready-made two-party flow: J-PAKE, RFC 8236 key confirmation and a session to encrypt with
 * the result. The messages are plain data classes; getting them to the other side is up to you.
 * 1. server -> client: ServerHello
 * 2. client -> server: ClientReply
 * 3. server -> client: ServerConfirm
 * 4. client -> server: ClientConfirm
 * After 4 the server has a JPakeSession, and the client has one if it verified the server's tag.
 * Both use the same password and the same JPakeConfig. For finer control use JPake directly.
 */

/** The server's first round */
class ServerHello(val round1: JPakeRound1)

/** The client's first and second round */
class ClientReply(val round1: JPakeRound1, val round2: JPakeRound2)

/** The server's second round and its key confirmation tag */
class ServerConfirm(val round2: JPakeRound2, val tag: ByteArray)

/** The client's key confirmation tag */
class ClientConfirm(val tag: ByteArray)

/**
 * @param serverVerified false if the server's tag didn't match, i.e. the passwords or the
 *   configs differ. [confirm] is still there to send, so the server finds out at once.
 */
class ClientConfirmResult(val confirm: ClientConfirm, val serverVerified: Boolean)

/** AES-GCM output: the random IV and the ciphertext with its authentication tag */
class SealedMessage(val iv: ByteArray, val cipherText: ByteArray)

class JPakeServer private constructor(
    private val jpake: JPake, private val config: JPakeConfig, val hello: ServerHello
) {
    private var keys: ConfirmedKeys? = null

    companion object {
        fun start(password: ByteArray, config: JPakeConfig = JPakeConfig()): JPakeServer {
            val jpake = JPake(JPakeRole.SERVER, password)
            return JPakeServer(jpake, config, ServerHello(jpake.writeRound1()))
        }
    }

    /** Frees the native state of an exchange that is dropped before [respond] completes */
    fun close() = jpake.close()

    /** Throws [JPakeException] if the client's rounds aren't valid J-PAKE messages */
    suspend fun respond(reply: ClientReply): ServerConfirm {
        jpake.readRound1(reply.round1)
        val round2 = jpake.writeRound2()
        jpake.readRound2(reply.round2)
        val confirmedKeys = ConfirmedKeys(
            jpake.deriveSecret(), JPakeRole.SERVER, config, hello.round1, reply.round1
        )
        keys = confirmedKeys
        return ServerConfirm(round2, confirmedKeys.localTag())
    }

    /** Throws [JPakeException] if the client's tag doesn't match, e.g. a wrong password */
    suspend fun finish(confirm: ClientConfirm): JPakeSession {
        val confirmedKeys = keys ?: throw JPakeException("respond() wasn't called")
        if (!confirmedKeys.verifyPeerTag(confirm.tag)) throw JPakeException("Key confirmation failed")
        return JPakeSession(confirmedKeys, config)
    }
}

class JPakeClient private constructor(
    private val jpake: JPake,
    private val config: JPakeConfig,
    private val serverRound1: JPakeRound1,
    val reply: ClientReply
) {
    private var verifiedKeys: ConfirmedKeys? = null

    companion object {
        /** Throws [JPakeException] if the server's round isn't a valid J-PAKE message */
        fun respond(
            password: ByteArray, hello: ServerHello, config: JPakeConfig = JPakeConfig()
        ): JPakeClient {
            val jpake = JPake(JPakeRole.CLIENT, password)
            jpake.readRound1(hello.round1)
            val reply = ClientReply(jpake.writeRound1(), jpake.writeRound2())
            return JPakeClient(jpake, config, hello.round1, reply)
        }
    }

    /** Frees the native state of an exchange that is dropped before [confirm] completes */
    fun close() = jpake.close()

    /** Throws [JPakeException] if the server's round isn't a valid J-PAKE message */
    suspend fun confirm(confirm: ServerConfirm): ClientConfirmResult {
        jpake.readRound2(confirm.round2)
        val confirmedKeys = ConfirmedKeys(
            jpake.deriveSecret(), JPakeRole.CLIENT, config, reply.round1, serverRound1
        )
        val verified = confirmedKeys.verifyPeerTag(confirm.tag)
        if (verified) verifiedKeys = confirmedKeys
        return ClientConfirmResult(ClientConfirm(confirmedKeys.localTag()), verified)
    }

    /** Throws [JPakeException] unless [confirm] verified the server's tag */
    fun session(): JPakeSession = JPakeSession(
        verifiedKeys ?: throw JPakeException("The server wasn't verified"), config
    )
}

/**
 * What both sides share once the keys are confirmed: keys derived for any purpose, and AES-256-GCM
 * for messages. Use a different [info] for every kind of message so the keys stay independent.
 */
class JPakeSession internal constructor(
    private val keys: ConfirmedKeys, private val config: JPakeConfig
) {
    suspend fun deriveKey(info: ByteArray, bits: Int = JPakeConfig.DERIVED_KEY_BITS): ByteArray =
        keys.deriveKey(info, bits)

    @OptIn(DelicateCryptographyApi::class)
    suspend fun seal(plaintext: ByteArray, info: ByteArray = config.encryptionLabel): SealedMessage {
        val iv = CryptographyRandom.nextBytes(config.ivBytes)
        val cipherText = aesGcmKey(info).cipher(config.tagBits.bits)
            .encryptWithIv(iv = iv, plaintext = plaintext)
        return SealedMessage(iv, cipherText)
    }

    /** Throws [JPakeException] if the message was altered, or [info] isn't the one it was sealed with */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun open(message: SealedMessage, info: ByteArray = config.encryptionLabel): ByteArray =
        try {
            aesGcmKey(info).cipher(config.tagBits.bits)
                .decryptWithIv(iv = message.iv, ciphertext = message.cipherText)
        } catch (_: Exception) {
            throw JPakeException("Can't open the message")
        }

    private suspend fun aesGcmKey(info: ByteArray) =
        CryptographyProvider.Default.get(AES.GCM).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, keys.deriveKey(info, JPakeConfig.DERIVED_KEY_BITS))
}
