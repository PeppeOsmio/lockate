package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.MemberMessage
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import com.peppeosmio.lockate.service.crypto.CryptoException
import com.peppeosmio.lockate.service.crypto.CryptoService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PayloadCodecTest {
    private val cryptoService = CryptoService()
    private val codec = PayloadCodec(cryptoService)
    private val key = cryptoService.generateGroupKey()
    private val coordinates = Coordinates(latitude = -33.868819712345, longitude = 151.209295512345)

    private fun transition(kind: TransitionKind) =
        CircleTransition(UUID.randomUUID().toString(), kind)

    private fun message(
        transitions: List<CircleTransition> = emptyList(),
        activity: UserActivity = UserActivity.Unknown
    ) = MemberMessage(
        coordinates, sentAtMillis = 1_780_000_000_123, transitions = transitions, activity = activity
    )

    @Test
    fun `a member message round trips`() = runTest {
        val sent = message(listOf(transition(TransitionKind.Enter), transition(TransitionKind.Exit)))

        val encrypted = codec.encryptMemberMessage(sent, key, "group", "member")
        val received = codec.decryptMemberMessage(encrypted, key, "group", "member")

        assertEquals(sent, received)
    }

    @Test
    fun `the activity round trips`() = runTest {
        UserActivity.entries.forEach { activity ->
            val encrypted = codec.encryptMemberMessage(message(activity = activity), key, "group", "member")

            assertEquals(activity, codec.decryptMemberMessage(encrypted, key, "group", "member").activity)
        }
    }

    @Test
    fun `messages with and without activity have the same size`() = runTest {
        val sizes = UserActivity.entries.map {
            codec.encryptMemberMessage(message(activity = it), key, "group", "member").cipherText.size
        }

        assertEquals(1, sizes.distinct().size)
    }

    @Test
    fun `messages with and without transitions have the same size`() = runTest {
        val plain = codec.encryptMemberMessage(message(), key, "group", "member")
        val full = codec.encryptMemberMessage(
            message(
                List(PayloadCodec.MAX_TRANSITIONS_PER_MESSAGE) { transition(TransitionKind.Enter) },
                UserActivity.InVehicle
            ),
            key, "group", "member"
        )

        assertEquals(plain.cipherText.size, full.cipherText.size)
        assertEquals(
            PayloadCodec.MEMBER_MESSAGE_SIZE + CryptoService.AES_GCM_TAG_BYTES,
            plain.cipherText.size
        )
    }

    @Test
    fun `a message can't be passed off as another member's`() = runTest {
        val encrypted = codec.encryptMemberMessage(message(), key, "group", "alice")

        try {
            codec.decryptMemberMessage(encrypted, key, "group", "bob")
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `a message can't be passed off as another group's`() = runTest {
        val encrypted = codec.encryptMemberMessage(message(), key, "group-1", "alice")

        try {
            codec.decryptMemberMessage(encrypted, key, "group-2", "alice")
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `a circle round trips`() = runTest {
        val circle = Circle(UUID.randomUUID().toString(), "Home", coordinates, 150.0)

        val encrypted = codec.encryptCircle(circle, key, "group")

        assertEquals(circle, codec.decryptCircle(encrypted, key, "group", circle.id))
    }

    @Test
    fun `circles have the same size whatever the name`() = runTest {
        val short = Circle(UUID.randomUUID().toString(), "A", coordinates, 100.0)
        val long = Circle(
            UUID.randomUUID().toString(),
            "😀".repeat(PayloadCodec.MAX_CIRCLE_NAME_CHARS),
            coordinates,
            1999.5
        )

        val a = codec.encryptCircle(short, key, "group")
        val b = codec.encryptCircle(long, key, "group")

        assertEquals(a.cipherText.size, b.cipherText.size)
        assertTrue(a.cipherText.size == PayloadCodec.CIRCLE_SIZE + CryptoService.AES_GCM_TAG_BYTES)
    }

    @Test
    fun `a circle can't be passed off under another id`() = runTest {
        val circle = Circle(UUID.randomUUID().toString(), "Home", coordinates, 150.0)
        val encrypted = codec.encryptCircle(circle, key, "group")

        try {
            codec.decryptCircle(encrypted, key, "group", UUID.randomUUID().toString())
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }

    @Test
    fun `a circle isn't a member message`() = runTest {
        val circle = Circle("shared-id", "Home", coordinates, 150.0)
        val encrypted = codec.encryptCircle(circle, key, "group")

        try {
            codec.decryptMemberMessage(encrypted, key, "group", "shared-id")
            fail("Expected CryptoException")
        } catch (e: CryptoException) {
            // expected
        }
    }
}
