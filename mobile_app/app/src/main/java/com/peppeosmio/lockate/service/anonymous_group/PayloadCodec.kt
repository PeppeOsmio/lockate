package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedMemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedPlace
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.domain.anonymous_group.PlaceTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import com.peppeosmio.lockate.domain.crypto.EncryptedData
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.crypto.Padding
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Member messages, place events, places and names are made of fields of a fixed length (every name,
 * of a group, a member or a place, is padded to one size), so the server learns nothing from the
 * length of what it stores or relays: not the hemisphere, the activity or the radius, not the
 * length of a name, not whether a place event is an arrival or a departure. Every field is bound to
 * the ciphertext as associated data together with the ids of the group and of the author (or of the
 * place), so the server can't hand one's data over as another's, nor one field as another.
 */
class PayloadCodec(private val cryptoService: CryptoService) {

    private fun aad(prefix: String, groupId: String, ownerId: String) =
        "$prefix|$groupId|$ownerId".toByteArray()

    private suspend fun decrypt(
        data: EncryptedData,
        key: ByteArray,
        aad: ByteArray,
        size: Int,
    ): ByteArray =
        cryptoService.decrypt(data, key, aad).also {
            if (it.size != size) throw CryptoException()
        }

    private suspend fun encryptName(name: String, key: ByteArray, aad: ByteArray): EncryptedData =
        cryptoService.encrypt(Padding.pad(name.toByteArray(), NAME_SIZE), key, aad)

    private suspend fun decryptName(data: EncryptedData, key: ByteArray, aad: ByteArray): String =
        Padding.unpad(decrypt(data, key, aad, NAME_SIZE)).decodeToString()

    suspend fun encryptGroupName(name: String, key: ByteArray, groupId: String): EncryptedData =
        encryptName(name, key, "$GROUP_NAME_AAD_PREFIX|$groupId".toByteArray())

    suspend fun decryptGroupName(data: EncryptedData, key: ByteArray, groupId: String): String =
        decryptName(data, key, "$GROUP_NAME_AAD_PREFIX|$groupId".toByteArray())

    suspend fun encryptMemberName(
        name: String,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): EncryptedData = encryptName(name, key, aad(MEMBER_NAME_AAD_PREFIX, groupId, memberId))

    suspend fun decryptMemberName(
        data: EncryptedData,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): String = decryptName(data, key, aad(MEMBER_NAME_AAD_PREFIX, groupId, memberId))

    private fun encodeCoordinates(coordinates: Coordinates): ByteArray =
        ByteBuffer.allocate(COORDINATES_BYTES)
            .putInt((coordinates.latitude * MICRODEGREES).roundToInt())
            .putInt((coordinates.longitude * MICRODEGREES).roundToInt())
            .array()

    private fun decodeCoordinates(bytes: ByteArray): Coordinates {
        val buffer = ByteBuffer.wrap(bytes)
        return Coordinates(
            latitude = buffer.getInt() / MICRODEGREES,
            longitude = buffer.getInt() / MICRODEGREES,
        )
    }

    suspend fun encryptMemberMessage(
        message: MemberMessage,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): EncryptedMemberMessage {
        val activity = byteArrayOf(ACTIVITY_CODES.indexOf(message.activity).toByte())
        return EncryptedMemberMessage(
            coordinates =
                cryptoService.encrypt(
                    encodeCoordinates(message.coordinates),
                    key,
                    aad(MEMBER_COORDINATES_AAD_PREFIX, groupId, memberId),
                ),
            activity =
                cryptoService.encrypt(
                    activity,
                    key,
                    aad(MEMBER_ACTIVITY_AAD_PREFIX, groupId, memberId),
                ),
        )
    }

    suspend fun decryptMemberMessage(
        message: EncryptedMemberMessage,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): MemberMessage {
        val coordinates =
            decrypt(
                message.coordinates,
                key,
                aad(MEMBER_COORDINATES_AAD_PREFIX, groupId, memberId),
                COORDINATES_BYTES,
            )
        val activity =
            decrypt(message.activity, key, aad(MEMBER_ACTIVITY_AAD_PREFIX, groupId, memberId), 1)
        return MemberMessage(
            coordinates = decodeCoordinates(coordinates),
            // an activity added by a newer version is read as unknown, so its position still counts
            activity = ACTIVITY_CODES.getOrNull(activity[0].toInt()) ?: UserActivity.Unknown,
        )
    }

    suspend fun encryptPlace(place: Place, key: ByteArray, groupId: String): EncryptedPlace =
        EncryptedPlace(
            coordinates =
                cryptoService.encrypt(
                    encodeCoordinates(place.center),
                    key,
                    aad(PLACE_COORDINATES_AAD_PREFIX, groupId, place.id),
                ),
            radius =
                cryptoService.encrypt(
                    ByteBuffer.allocate(RADIUS_BYTES).putDouble(place.radiusMeters).array(),
                    key,
                    aad(PLACE_RADIUS_AAD_PREFIX, groupId, place.id),
                ),
            name = encryptName(place.name, key, aad(PLACE_NAME_AAD_PREFIX, groupId, place.id)),
        )

    suspend fun decryptPlace(
        place: EncryptedPlace,
        key: ByteArray,
        groupId: String,
        placeId: String,
    ): Place {
        val coordinates =
            decrypt(
                place.coordinates,
                key,
                aad(PLACE_COORDINATES_AAD_PREFIX, groupId, placeId),
                COORDINATES_BYTES,
            )
        val radius =
            ByteBuffer.wrap(
                    decrypt(
                        place.radius,
                        key,
                        aad(PLACE_RADIUS_AAD_PREFIX, groupId, placeId),
                        RADIUS_BYTES,
                    )
                )
                .getDouble()
        if (!radius.isFinite() || radius <= 0) throw CryptoException()
        val name = decryptName(place.name, key, aad(PLACE_NAME_AAD_PREFIX, groupId, placeId))
        return Place(
            id = placeId,
            name = name,
            center = decodeCoordinates(coordinates),
            radiusMeters = radius,
        )
    }

    suspend fun encryptPlaceEvent(
        event: PlaceTransition,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): EncryptedData {
        val placeId = UUID.fromString(event.placeId)
        val plaintext =
            ByteBuffer.allocate(PLACE_EVENT_BYTES)
                .putLong(placeId.mostSignificantBits)
                .putLong(placeId.leastSignificantBits)
                .put(TRANSITION_CODES.indexOf(event.kind).toByte())
                .array()
        return cryptoService.encrypt(
            plaintext,
            key,
            aad(PLACE_EVENT_AAD_PREFIX, groupId, memberId),
        )
    }

    suspend fun decryptPlaceEvent(
        data: EncryptedData,
        key: ByteArray,
        groupId: String,
        memberId: String,
    ): PlaceTransition {
        val buffer =
            ByteBuffer.wrap(
                decrypt(
                    data,
                    key,
                    aad(PLACE_EVENT_AAD_PREFIX, groupId, memberId),
                    PLACE_EVENT_BYTES,
                )
            )
        val placeId = UUID(buffer.getLong(), buffer.getLong()).toString()
        val kind = TRANSITION_CODES.getOrNull(buffer.get().toInt()) ?: throw CryptoException()
        return PlaceTransition(placeId, kind)
    }

    companion object {
        private const val COORDINATES_BYTES = 8
        private const val MICRODEGREES = 1_000_000.0
        private const val RADIUS_BYTES = 8
        // the index is the byte on the wire, so new activities go at the end
        private val ACTIVITY_CODES =
            listOf(
                UserActivity.Unknown,
                UserActivity.Still,
                UserActivity.OnFoot,
                UserActivity.OnBicycle,
                UserActivity.InVehicle,
            )
        private const val PLACE_EVENT_BYTES = 17
        private val TRANSITION_CODES = listOf(TransitionKind.Enter, TransitionKind.Exit)
        const val MAX_NAME_CHARS = 40
        // a char takes at most 3 bytes in UTF-8, plus the padding marker
        const val NAME_SIZE = 128
        private const val GROUP_NAME_AAD_PREFIX = "lockate-group-name-v1"
        private const val MEMBER_NAME_AAD_PREFIX = "lockate-member-name-v1"
        private const val MEMBER_COORDINATES_AAD_PREFIX = "lockate-member-coords-v1"
        private const val MEMBER_ACTIVITY_AAD_PREFIX = "lockate-member-activity-v1"
        // places were called circles: the AADs keep that name so the places already stored stay
        // readable
        private const val PLACE_COORDINATES_AAD_PREFIX = "lockate-circle-coords-v1"
        private const val PLACE_RADIUS_AAD_PREFIX = "lockate-circle-radius-v1"
        private const val PLACE_NAME_AAD_PREFIX = "lockate-circle-name-v1"
        private const val PLACE_EVENT_AAD_PREFIX = "lockate-circle-event-v1"
    }
}
