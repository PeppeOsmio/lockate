package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedCircle
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedMemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.crypto.Padding
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Member messages and circles are made of fields of a fixed length (a circle's name is padded to
 * one size), so the server learns nothing from the length of what it relays: not the hemisphere,
 * the activity or the radius, not the length of a circle's name. Every field is bound to the
 * ciphertext as associated data together with the ids of the group and of the author (or of the
 * circle), so the server can't hand one's data over as another's, nor one field as another.
 */
class PayloadCodec(private val cryptoService: CryptoService) {

    private fun aad(prefix: String, groupId: String, ownerId: String) =
        "$prefix|$groupId|$ownerId".toByteArray()

    private suspend fun decrypt(
        data: EncryptedData, key: ByteArray, aad: ByteArray, size: Int
    ): ByteArray = cryptoService.decrypt(data, key, aad).also {
        if (it.size != size) throw CryptoException()
    }

    private fun encodeCoordinates(coordinates: Coordinates): ByteArray =
        ByteBuffer.allocate(COORDINATES_BYTES)
            .putInt((coordinates.latitude * MICRODEGREES).roundToInt())
            .putInt((coordinates.longitude * MICRODEGREES).roundToInt())
            .array()

    private fun decodeCoordinates(bytes: ByteArray): Coordinates {
        val buffer = ByteBuffer.wrap(bytes)
        return Coordinates(
            latitude = buffer.getInt() / MICRODEGREES,
            longitude = buffer.getInt() / MICRODEGREES
        )
    }

    suspend fun encryptMemberMessage(
        message: MemberMessage, key: ByteArray, groupId: String, memberId: String
    ): EncryptedMemberMessage {
        val activity = byteArrayOf(ACTIVITY_CODES.indexOf(message.activity).toByte())
        return EncryptedMemberMessage(
            coordinates = cryptoService.encrypt(
                encodeCoordinates(message.coordinates),
                key,
                aad(MEMBER_COORDINATES_AAD_PREFIX, groupId, memberId)
            ),
            activity = cryptoService.encrypt(
                activity, key, aad(MEMBER_ACTIVITY_AAD_PREFIX, groupId, memberId)
            )
        )
    }

    suspend fun decryptMemberMessage(
        message: EncryptedMemberMessage, key: ByteArray, groupId: String, memberId: String
    ): MemberMessage {
        val coordinates = decrypt(
            message.coordinates,
            key,
            aad(MEMBER_COORDINATES_AAD_PREFIX, groupId, memberId),
            COORDINATES_BYTES
        )
        val activity =
            decrypt(message.activity, key, aad(MEMBER_ACTIVITY_AAD_PREFIX, groupId, memberId), 1)
        return MemberMessage(
            coordinates = decodeCoordinates(coordinates),
            // an activity added by a newer version is read as unknown, so its position still counts
            activity = ACTIVITY_CODES.getOrNull(activity[0].toInt()) ?: UserActivity.Unknown
        )
    }

    suspend fun encryptCircle(circle: Circle, key: ByteArray, groupId: String): EncryptedCircle =
        EncryptedCircle(
            coordinates = cryptoService.encrypt(
                encodeCoordinates(circle.center),
                key,
                aad(CIRCLE_COORDINATES_AAD_PREFIX, groupId, circle.id)
            ),
            radius = cryptoService.encrypt(
                ByteBuffer.allocate(RADIUS_BYTES).putDouble(circle.radiusMeters).array(),
                key,
                aad(CIRCLE_RADIUS_AAD_PREFIX, groupId, circle.id)
            ),
            name = cryptoService.encrypt(
                Padding.pad(circle.name.toByteArray(), CIRCLE_NAME_SIZE),
                key,
                aad(CIRCLE_NAME_AAD_PREFIX, groupId, circle.id)
            )
        )

    suspend fun decryptCircle(
        circle: EncryptedCircle, key: ByteArray, groupId: String, circleId: String
    ): Circle {
        val coordinates = decrypt(
            circle.coordinates,
            key,
            aad(CIRCLE_COORDINATES_AAD_PREFIX, groupId, circleId),
            COORDINATES_BYTES
        )
        val radius = ByteBuffer.wrap(
            decrypt(circle.radius, key, aad(CIRCLE_RADIUS_AAD_PREFIX, groupId, circleId), RADIUS_BYTES)
        ).getDouble()
        if (!radius.isFinite() || radius <= 0) throw CryptoException()
        val name = Padding.unpad(
            decrypt(
                circle.name,
                key,
                aad(CIRCLE_NAME_AAD_PREFIX, groupId, circleId),
                CIRCLE_NAME_SIZE
            )
        )
        return Circle(
            id = circleId,
            name = name.decodeToString(),
            center = decodeCoordinates(coordinates),
            radiusMeters = radius
        )
    }

    companion object {
        private const val COORDINATES_BYTES = 8
        private const val MICRODEGREES = 1_000_000.0
        private const val RADIUS_BYTES = 8
        // the index is the byte on the wire, so new activities go at the end
        private val ACTIVITY_CODES = listOf(
            UserActivity.Unknown,
            UserActivity.Still,
            UserActivity.OnFoot,
            UserActivity.OnBicycle,
            UserActivity.InVehicle
        )
        const val MAX_CIRCLE_NAME_CHARS = 40
        // a char takes at most 3 bytes in UTF-8, plus the padding marker
        const val CIRCLE_NAME_SIZE = 128
        private const val MEMBER_COORDINATES_AAD_PREFIX = "lockate-member-coords-v1"
        private const val MEMBER_ACTIVITY_AAD_PREFIX = "lockate-member-activity-v1"
        private const val CIRCLE_COORDINATES_AAD_PREFIX = "lockate-circle-coords-v1"
        private const val CIRCLE_RADIUS_AAD_PREFIX = "lockate-circle-radius-v1"
        private const val CIRCLE_NAME_AAD_PREFIX = "lockate-circle-name-v1"
    }
}
