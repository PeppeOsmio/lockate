package com.peppeosmio.jpake

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512

/** The hash behind HKDF and the HMAC key confirmation tags; the tags are as long as the hash output */
enum class KdfHash { SHA256, SHA512 }

/**
 * Everything the key schedule and the ready-made flow let you choose. The defaults follow RFC 8236
 * (sections 2.2 and 5). Both sides must use the same values, so an application that wants its
 * keys and tags tied to its own protocol sets at least [context], and the IDs if it has them.
 *
 * @param context mixed into every derived key as the HKDF salt, e.g. a session id
 * @param confirmationLabel makes the confirmation key: `k' = HKDF(secret, context, label)`
 * @param tagLabel and [serverId] and [clientId] go into the tags exactly as in RFC 8236:
 *   `MAC(k', tagLabel || myId || peerId || myKeyShares || peerKeyShares)`
 * @param encryptionLabel the default HKDF info for [JPakeSession.seal] and [JPakeSession.open]
 * @param ivBytes size of the random AES-GCM IV that [JPakeSession.seal] makes
 * @param tagBits size of the AES-GCM authentication tag
 */
class JPakeConfig(
    val hash: KdfHash = KdfHash.SHA256,
    val context: ByteArray = ByteArray(0),
    val confirmationLabel: ByteArray = DEFAULT_CONFIRMATION_LABEL,
    val tagLabel: ByteArray = DEFAULT_TAG_LABEL,
    val serverId: ByteArray = DEFAULT_SERVER_ID,
    val clientId: ByteArray = DEFAULT_CLIENT_ID,
    val encryptionLabel: ByteArray = DEFAULT_ENCRYPTION_LABEL,
    val ivBytes: Int = DEFAULT_IV_BYTES,
    val tagBits: Int = DEFAULT_TAG_BITS,
) {
    companion object {
        val DEFAULT_CONFIRMATION_LABEL = "JPAKE_KC".encodeToByteArray()
        val DEFAULT_TAG_LABEL = "KC_1_U".encodeToByteArray()
        val DEFAULT_SERVER_ID = "server".encodeToByteArray()
        val DEFAULT_CLIENT_ID = "client".encodeToByteArray()
        val DEFAULT_ENCRYPTION_LABEL = "JPAKE_ENC".encodeToByteArray()
        const val DEFAULT_IV_BYTES = 12
        const val DEFAULT_TAG_BITS = 128
        const val DERIVED_KEY_BITS = 256
    }
}

internal fun KdfHash.digestId() = when (this) {
    KdfHash.SHA256 -> CryptographyProvider.Default.get(SHA256).id
    KdfHash.SHA512 -> CryptographyProvider.Default.get(SHA512).id
}

/**
 * The key schedule and the explicit key confirmation RFC 8236 recommends, for the low-level API.
 * Built from a party's [secret][JPake.deriveSecret] and the first rounds of both sides.
 * Each side sends its [localTag] and checks the other's with [verifyPeerTag]; only if that passes
 * did both derive the same secret, and only then should [deriveKey] be used.
 *
 * @param localRound1 this party's own first round, [peerRound1] the one it received
 */
class ConfirmedKeys(
    private val secret: ByteArray,
    private val role: JPakeRole,
    private val config: JPakeConfig,
    localRound1: JPakeRound1,
    peerRound1: JPakeRound1,
) {
    private val localShares = localRound1.keyShares()
    private val peerShares = peerRound1.keyShares()

    private val localId = if (role == JPakeRole.SERVER) config.serverId else config.clientId
    private val peerId = if (role == JPakeRole.SERVER) config.clientId else config.serverId

    /** This party's confirmation tag, to send to the peer */
    suspend fun localTag(): ByteArray =
        tag(localId, peerId, localShares, peerShares)

    /** Checks the peer's tag in constant time; false if the two sides didn't derive the same secret */
    suspend fun verifyPeerTag(tag: ByteArray): Boolean =
        constantTimeEquals(tag(peerId, localId, peerShares, localShares), tag)

    /** A key derived from the secret for one purpose; keys for different [info] are independent */
    suspend fun deriveKey(
        info: ByteArray, bits: Int = JPakeConfig.DERIVED_KEY_BITS
    ): ByteArray = CryptographyProvider.Default.get(HKDF).secretDerivation(
        digest = config.hash.digestId(), outputSize = bits.bits, salt = config.context, info = info
    ).deriveSecret(secret).toByteArray()

    private suspend fun tag(
        senderId: ByteArray, receiverId: ByteArray,
        senderShares: List<ByteArray>, receiverShares: List<ByteArray>
    ): ByteArray {
        val confirmationKey = deriveKey(config.confirmationLabel)
        val input = config.tagLabel + senderId + receiverId +
            senderShares.fold(ByteArray(0)) { acc, share -> acc + share } +
            receiverShares.fold(ByteArray(0)) { acc, share -> acc + share }
        return CryptographyProvider.Default.get(HMAC).keyDecoder(config.hash.digestId())
            .decodeFromByteArray(HMAC.Key.Format.RAW, confirmationKey)
            .signatureGenerator()
            .generateSignature(input)
    }
}
