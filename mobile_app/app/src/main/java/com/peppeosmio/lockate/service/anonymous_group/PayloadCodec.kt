package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.crypto.Padding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Member messages and circles all have the same padded size, so the server can't tell a message
 * with transitions from one without, and can't guess the length of a circle's name. The ids of the
 * group and of the author (or of the circle) are bound to the ciphertext as associated data, so
 * the server can't hand one's data over as another's.
 */
class PayloadCodec(private val cryptoService: CryptoService) {

    @Serializable
    private data class TransitionPayload(
        val circleId: String, val kind: String
    )

    @Serializable
    private data class MemberMessagePayload(
        val lat: Double,
        val lon: Double,
        val ts: Long,
        val transitions: List<TransitionPayload> = emptyList(),
        val act: String? = null
    )

    @Serializable
    private data class CirclePayload(
        val name: String,
        val lat: Double,
        val lon: Double,
        @SerialName("radius") val radiusMeters: Double
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun memberAad(groupId: String, memberId: String) =
        "$MEMBER_MESSAGE_AAD_PREFIX|$groupId|$memberId".toByteArray()

    private fun circleAad(groupId: String, circleId: String) =
        "$CIRCLE_AAD_PREFIX|$groupId|$circleId".toByteArray()

    suspend fun encryptMemberMessage(
        message: MemberMessage, key: ByteArray, groupId: String, memberId: String
    ): EncryptedData {
        val payload = MemberMessagePayload(
            lat = message.coordinates.latitude,
            lon = message.coordinates.longitude,
            ts = message.sentAtMillis,
            transitions = message.transitions.map {
                TransitionPayload(it.circleId, it.kind.name.lowercase())
            },
            act = ACTIVITY_NAMES[message.activity]
        )
        val padded = Padding.pad(
            json.encodeToString(MemberMessagePayload.serializer(), payload).toByteArray(),
            MEMBER_MESSAGE_SIZE
        )
        return cryptoService.encrypt(padded, key, memberAad(groupId, memberId))
    }

    suspend fun decryptMemberMessage(
        data: EncryptedData, key: ByteArray, groupId: String, memberId: String
    ): MemberMessage {
        val plaintext = Padding.unpad(cryptoService.decrypt(data, key, memberAad(groupId, memberId)))
        try {
            val payload =
                json.decodeFromString(MemberMessagePayload.serializer(), plaintext.decodeToString())
            return MemberMessage(
                coordinates = Coordinates(payload.lat, payload.lon),
                sentAtMillis = payload.ts,
                // a kind added by a newer version is skipped, so its position still counts
                transitions = payload.transitions.mapNotNull {
                    TransitionKind.entries.firstOrNull { kind ->
                        kind.name.equals(it.kind, ignoreCase = true)
                    }?.let { kind -> CircleTransition(circleId = it.circleId, kind = kind) }
                },
                activity = ACTIVITY_NAMES.entries.firstOrNull { it.value == payload.act }?.key
                    ?: UserActivity.Unknown
            )
        } catch (e: Exception) {
            throw CryptoException()
        }
    }

    suspend fun encryptCircle(
        circle: Circle, key: ByteArray, groupId: String
    ): EncryptedData {
        val payload = CirclePayload(
            name = circle.name,
            lat = circle.center.latitude,
            lon = circle.center.longitude,
            radiusMeters = circle.radiusMeters
        )
        val padded = Padding.pad(
            json.encodeToString(CirclePayload.serializer(), payload).toByteArray(), CIRCLE_SIZE
        )
        return cryptoService.encrypt(padded, key, circleAad(groupId, circle.id))
    }

    suspend fun decryptCircle(
        data: EncryptedData, key: ByteArray, groupId: String, circleId: String
    ): Circle {
        val plaintext = Padding.unpad(cryptoService.decrypt(data, key, circleAad(groupId, circleId)))
        try {
            val payload =
                json.decodeFromString(CirclePayload.serializer(), plaintext.decodeToString())
            return Circle(
                id = circleId,
                name = payload.name,
                center = Coordinates(payload.lat, payload.lon),
                radiusMeters = payload.radiusMeters
            )
        } catch (e: Exception) {
            throw CryptoException()
        }
    }

    companion object {
        const val MEMBER_MESSAGE_SIZE = 512
        private val ACTIVITY_NAMES = mapOf(
            UserActivity.Still to "still",
            UserActivity.OnFoot to "foot",
            UserActivity.OnBicycle to "bike",
            UserActivity.InVehicle to "vehicle"
        )
        const val CIRCLE_SIZE = 384
        const val MAX_TRANSITIONS_PER_MESSAGE = 4
        const val MAX_CIRCLE_NAME_CHARS = 40
        private const val MEMBER_MESSAGE_AAD_PREFIX = "lockate-member-msg-v1"
        private const val CIRCLE_AAD_PREFIX = "lockate-circle-v1"
    }
}
