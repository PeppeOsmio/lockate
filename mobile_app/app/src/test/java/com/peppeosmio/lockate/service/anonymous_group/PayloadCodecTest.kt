package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedMemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PayloadCodecTest {
    private val cryptoService = CryptoService()
    private val codec = PayloadCodec(cryptoService)
    private val key = cryptoService.generateGroupKey()
    private val coordinates = Coordinates(latitude = -33.868819712345, longitude = 151.209295512345)

    private fun message(
        coordinates: Coordinates = this.coordinates,
        activity: UserActivity = UserActivity.Unknown
    ) = MemberMessage(coordinates, activity)

    private suspend fun assertCryptoException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `the coordinates round trip to the microdegree`() = runTest {
        listOf(
            coordinates,
            Coordinates(90.0, 180.0),
            Coordinates(-90.0, -180.0),
            Coordinates(0.0, 0.0),
            Coordinates(-0.0000004, 0.0000006)
        ).forEach { sent ->
            val encrypted = codec.encryptMemberMessage(message(sent), key, "group", "member")
            val received = codec.decryptMemberMessage(encrypted, key, "group", "member").coordinates

            assertEquals(sent.latitude, received.latitude, 0.0000005)
            assertEquals(sent.longitude, received.longitude, 0.0000005)
        }
    }

    @Test
    fun `the activity round trips`() = runTest {
        UserActivity.entries.forEach { activity ->
            val encrypted = codec.encryptMemberMessage(message(activity = activity), key, "group", "member")

            assertEquals(activity, codec.decryptMemberMessage(encrypted, key, "group", "member").activity)
        }
    }

    @Test
    fun `every message has the same size whatever it says`() = runTest {
        val messages = UserActivity.entries.flatMap { activity ->
            listOf(Coordinates(0.0, 0.0), Coordinates(-89.123456, -179.654321), coordinates)
                .map { message(it, activity) }
        }

        messages.forEach {
            val encrypted = codec.encryptMemberMessage(it, key, "group", "member")
            assertEquals(8 + CryptoService.AES_GCM_TAG_BYTES, encrypted.coordinates.cipherText.size)
            assertEquals(1 + CryptoService.AES_GCM_TAG_BYTES, encrypted.activity.cipherText.size)
        }
    }

    @Test
    fun `an activity unknown to this version is read as unknown and the position is kept`() = runTest {
        val sent = codec.encryptMemberMessage(message(), key, "group", "member")
        val newer = cryptoService.encrypt(
            byteArrayOf(42), key, "lockate-member-activity-v1|group|member".toByteArray()
        )

        val received =
            codec.decryptMemberMessage(sent.copy(activity = newer), key, "group", "member")

        assertEquals(UserActivity.Unknown, received.activity)
        assertEquals(coordinates.latitude, received.coordinates.latitude, 0.0000005)
    }

    @Test
    fun `fields of the wrong length are rejected`() = runTest {
        val sent = codec.encryptMemberMessage(message(), key, "group", "member")
        val longCoordinates = cryptoService.encrypt(
            ByteArray(9), key, "lockate-member-coords-v1|group|member".toByteArray()
        )
        val emptyActivity = cryptoService.encrypt(
            ByteArray(0), key, "lockate-member-activity-v1|group|member".toByteArray()
        )

        assertCryptoException {
            codec.decryptMemberMessage(sent.copy(coordinates = longCoordinates), key, "group", "member")
        }
        assertCryptoException {
            codec.decryptMemberMessage(sent.copy(activity = emptyActivity), key, "group", "member")
        }
    }

    @Test
    fun `a message can't be passed off as another member's`() = runTest {
        val encrypted = codec.encryptMemberMessage(message(), key, "group", "alice")
        val bobs = codec.encryptMemberMessage(message(), key, "group", "bob")

        assertCryptoException { codec.decryptMemberMessage(encrypted, key, "group", "bob") }
        assertCryptoException {
            codec.decryptMemberMessage(bobs.copy(activity = encrypted.activity), key, "group", "bob")
        }
    }

    @Test
    fun `a message can't be passed off as another group's`() = runTest {
        val encrypted = codec.encryptMemberMessage(message(), key, "group-1", "alice")

        assertCryptoException { codec.decryptMemberMessage(encrypted, key, "group-2", "alice") }
    }

    @Test
    fun `the coordinates and the activity can't be swapped`() = runTest {
        val encrypted = codec.encryptMemberMessage(message(), key, "group", "member")
        val swapped = EncryptedMemberMessage(
            coordinates = encrypted.activity, activity = encrypted.coordinates
        )

        assertCryptoException { codec.decryptMemberMessage(swapped, key, "group", "member") }
    }

    private fun circle(
        name: String = "Home", center: Coordinates = coordinates, radiusMeters: Double = 150.0
    ) = Circle(UUID.randomUUID().toString(), name, center, radiusMeters)

    @Test
    fun `a circle round trips`() = runTest {
        val sent = circle(name = "Scuola 😀", radiusMeters = 1999.5)

        val received = codec.decryptCircle(codec.encryptCircle(sent, key, "group"), key, "group", sent.id)

        assertEquals(sent.name, received.name)
        assertEquals(sent.radiusMeters, received.radiusMeters, 0.0)
        assertEquals(sent.center.latitude, received.center.latitude, 0.0000005)
        assertEquals(sent.center.longitude, received.center.longitude, 0.0000005)
    }

    @Test
    fun `every circle has the same size whatever it says`() = runTest {
        val circles = listOf(
            circle(name = "A", center = Coordinates(0.0, 0.0), radiusMeters = 30.0),
            circle(
                name = "😀".repeat(PayloadCodec.MAX_NAME_CHARS / 2),
                center = Coordinates(-89.123456, -179.654321),
                radiusMeters = 1999.5
            ),
            circle(name = "€".repeat(PayloadCodec.MAX_NAME_CHARS))
        )

        circles.forEach {
            val encrypted = codec.encryptCircle(it, key, "group")
            assertEquals(8 + CryptoService.AES_GCM_TAG_BYTES, encrypted.coordinates.cipherText.size)
            assertEquals(8 + CryptoService.AES_GCM_TAG_BYTES, encrypted.radius.cipherText.size)
            assertEquals(
                PayloadCodec.NAME_SIZE + CryptoService.AES_GCM_TAG_BYTES,
                encrypted.name.cipherText.size
            )
        }
    }

    @Test
    fun `a circle can't be passed off under another id`() = runTest {
        val sent = circle()
        val encrypted = codec.encryptCircle(sent, key, "group")
        val other = codec.encryptCircle(circle(), key, "group")

        assertCryptoException {
            codec.decryptCircle(encrypted, key, "group", UUID.randomUUID().toString())
        }
        assertCryptoException {
            codec.decryptCircle(encrypted.copy(name = other.name), key, "group", sent.id)
        }
    }

    @Test
    fun `the centre and the radius of a circle can't be swapped`() = runTest {
        val sent = circle()
        val encrypted = codec.encryptCircle(sent, key, "group")

        assertCryptoException {
            codec.decryptCircle(
                encrypted.copy(coordinates = encrypted.radius, radius = encrypted.coordinates),
                key, "group", sent.id
            )
        }
    }

    @Test
    fun `a circle's centre isn't a member's position`() = runTest {
        val sent = circle().copy(id = "shared-id")
        val encryptedCircle = codec.encryptCircle(sent, key, "group")
        val message = codec.encryptMemberMessage(message(), key, "group", "shared-id")

        assertCryptoException {
            codec.decryptMemberMessage(
                message.copy(coordinates = encryptedCircle.coordinates), key, "group", "shared-id"
            )
        }
    }

    @Test
    fun `group and member names round trip`() = runTest {
        val groupName = codec.encryptGroupName("Famiglia 😀", key, "group")
        val memberName = codec.encryptMemberName("Peppe", key, "group", "member")

        assertEquals("Famiglia 😀", codec.decryptGroupName(groupName, key, "group"))
        assertEquals("Peppe", codec.decryptMemberName(memberName, key, "group", "member"))
    }

    @Test
    fun `every name has the same size whatever it says`() = runTest {
        listOf("A", "😀".repeat(PayloadCodec.MAX_NAME_CHARS / 2), "€".repeat(PayloadCodec.MAX_NAME_CHARS))
            .forEach {
                val size = PayloadCodec.NAME_SIZE + CryptoService.AES_GCM_TAG_BYTES
                assertEquals(size, codec.encryptGroupName(it, key, "group").cipherText.size)
                assertEquals(size, codec.encryptMemberName(it, key, "group", "member").cipherText.size)
            }
    }

    @Test
    fun `a member's name can't be passed off as another member's or as the group's`() = runTest {
        val name = codec.encryptMemberName("Peppe", key, "group", "member")

        assertCryptoException { codec.decryptMemberName(name, key, "group", "other-member") }
        assertCryptoException { codec.decryptMemberName(name, key, "other-group", "member") }
        assertCryptoException { codec.decryptGroupName(name, key, "group") }
    }

    @Test
    fun `a group's name can't be passed off as another group's or as a member's`() = runTest {
        val name = codec.encryptGroupName("Famiglia", key, "group")

        assertCryptoException { codec.decryptGroupName(name, key, "other-group") }
        assertCryptoException { codec.decryptMemberName(name, key, "group", "member") }
    }

    @Test
    fun `an unpadded name is rejected`() = runTest {
        val unpadded = cryptoService.encrypt(
            "Peppe".toByteArray(), key, "lockate-group-name-v1|group".toByteArray()
        )

        assertCryptoException { codec.decryptGroupName(unpadded, key, "group") }
    }

    @Test
    fun `a circle event round trips`() = runTest {
        val circleId = UUID.randomUUID().toString()
        TransitionKind.entries.forEach { kind ->
            val encrypted =
                codec.encryptCircleEvent(CircleTransition(circleId, kind), key, "group", "member")

            assertEquals(
                CircleTransition(circleId, kind),
                codec.decryptCircleEvent(encrypted, key, "group", "member")
            )
            assertEquals(17 + CryptoService.AES_GCM_TAG_BYTES, encrypted.cipherText.size)
        }
    }

    @Test
    fun `a circle event can't be passed off as another member's or another group's`() = runTest {
        val event = codec.encryptCircleEvent(
            CircleTransition(UUID.randomUUID().toString(), TransitionKind.Enter), key, "group", "member"
        )

        assertCryptoException { codec.decryptCircleEvent(event, key, "group", "other-member") }
        assertCryptoException { codec.decryptCircleEvent(event, key, "other-group", "member") }
    }

    @Test
    fun `a circle event with an unknown kind is rejected`() = runTest {
        val unknownKind = cryptoService.encrypt(
            ByteArray(17).also { it[16] = 2 }, key, "lockate-circle-event-v1|group|member".toByteArray()
        )

        assertCryptoException { codec.decryptCircleEvent(unknownKind, key, "group", "member") }
    }
}
