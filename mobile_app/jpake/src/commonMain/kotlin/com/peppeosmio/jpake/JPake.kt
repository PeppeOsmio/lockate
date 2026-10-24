package com.peppeosmio.jpake

internal const val JPAKE_STEP_KEY_SHARE = 1
internal const val JPAKE_STEP_ZK_PUBLIC = 2
internal const val JPAKE_STEP_ZK_PROOF = 3

internal const val JPAKE_KEY_BYTES = 32

/** One J-PAKE party in Mbed TLS (see `jpake-native`), a step at a time */
internal interface JPakeNative {
    /** Throws [JPakeNativeException] if Mbed TLS refuses the step */
    fun output(step: Int): ByteArray

    /** Throws [JPakeNativeException] if Mbed TLS refuses the step, e.g. an invalid point or proof */
    fun input(step: Int, data: ByteArray)

    fun deriveKey(): ByteArray

    fun close()
}

internal class JPakeNativeException(val status: Int) : Exception("Mbed TLS J-PAKE error $status")

internal expect fun createJPakeNative(isServer: Boolean, password: ByteArray): JPakeNative

/** The exchange failed: a message is malformed, tampered with, or a check didn't pass */
class JPakeException(message: String = "J-PAKE exchange failed") : Exception(message)

/**
 * The two sides of J-PAKE. They only differ in the order they may call the steps in, see
 * [JPake]. Mbed TLS' zero-knowledge proofs name them "server" and "client", and that isn't
 * configurable.
 */
enum class JPakeRole { CLIENT, SERVER }

/**
 * A party's first round: two key shares (curve points), each with a zero-knowledge proof, as the
 * bytes to send to the peer.
 */
class JPakeRound1(val bytes: ByteArray) {
    /** The two key shares as uncompressed points, which RFC 8236 key confirmation covers */
    fun keyShares(): List<ByteArray> {
        val parts = lvSplit(bytes)?.takeIf { it.size == ROUND_ONE_STEPS.size }
            ?: throw JPakeException()
        return listOf(parts[0], parts[3])
    }
}

/** A party's second round: a key share with its zero-knowledge proof, as the bytes to send */
class JPakeRound2(val bytes: ByteArray)

private val ROUND_ONE_STEPS = listOf(
    JPAKE_STEP_KEY_SHARE, JPAKE_STEP_ZK_PUBLIC, JPAKE_STEP_ZK_PROOF,
    JPAKE_STEP_KEY_SHARE, JPAKE_STEP_ZK_PUBLIC, JPAKE_STEP_ZK_PROOF
)
private val ROUND_TWO_STEPS = listOf(JPAKE_STEP_KEY_SHARE, JPAKE_STEP_ZK_PUBLIC, JPAKE_STEP_ZK_PROOF)

/**
 * EC-JPAKE (RFC 8236) on P-256 with SHA-256, from Mbed TLS: the low-level API. Both sides get the
 * same [deriveSecret] only if they used the same password, and neither learns anything about the
 * other's password from a failed exchange, but nothing here tells you whether the secrets match.
 * For that, use [ConfirmedKeys] or the ready-made flow ([JPakeServer] and [JPakeClient]).
 *
 * Mbed TLS enforces this order. The server: [writeRound1], then [readRound1] (the client's),
 * [writeRound2], [readRound2]. The client: [readRound1] (the server's), [writeRound1], then
 * [writeRound2] and [readRound2] in either order. Both finish with [deriveSecret].
 *
 * Any failure frees the native state and throws [JPakeException]; the instance can't be reused.
 */
class JPake(role: JPakeRole, password: ByteArray) {
    private val native = createJPakeNative(role == JPakeRole.SERVER, password)

    fun writeRound1(): JPakeRound1 = JPakeRound1(write(ROUND_ONE_STEPS))

    fun readRound1(round: JPakeRound1) = read(ROUND_ONE_STEPS, round.bytes)

    fun writeRound2(): JPakeRound2 = JPakeRound2(write(ROUND_TWO_STEPS))

    fun readRound2(round: JPakeRound2) = read(ROUND_TWO_STEPS, round.bytes)

    /** Frees the native state; safe to call more than once */
    fun close() = native.close()

    /**
     * 32 bytes: SHA-256 of the shared point's x coordinate (`PSA_ALG_TLS12_ECJPAKE_TO_PMS`). Frees
     * the native state, so it can only be called once.
     */
    fun deriveSecret(): ByteArray = guarded { native.deriveKey() }.also { native.close() }

    private fun write(steps: List<Int>): ByteArray =
        guarded { lvCat(*Array(steps.size) { native.output(steps[it]) }) }

    private fun read(steps: List<Int>, message: ByteArray) {
        val parts = lvSplit(message)?.takeIf { it.size == steps.size }
        guarded {
            if (parts == null) throw JPakeException()
            steps.forEachIndexed { i, step -> native.input(step, parts[i]) }
        }
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: JPakeNativeException) {
        native.close()
        throw JPakeException()
    } catch (e: JPakeException) {
        native.close()
        throw e
    }
}
