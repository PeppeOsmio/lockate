package com.peppeosmio.lockate.service.invite

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InviteExchangeTest {

    private val code = InviteCode(7, listOf("acid", "acorn", "acre"))
    private val wrongWords = listOf("acid", "acorn", "acted")
    private val payload = InvitePayload(
        groupId = "69e74d01-ef20-47af-b602-0b45776d8666",
        groupKey = ByteArray(32) { it.toByte() },
        joinToken = ByteArray(32) { (it * 3).toByte() }
    )

    private fun ByteArray.flipBit(index: Int = 0) =
        copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }

    private fun ByteArray.flipMiddleBit() = flipBit(size / 2)

    private suspend fun assertAborts(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected InviteAbortedException")
        } catch (_: InviteAbortedException) {
        }
    }

    private class Exchange(val inviter: Inviter, val invitee: Invitee, val confirmation: InviterConfirmation)

    private suspend fun exchange(inviteeWords: List<String>): Exchange {
        val inviter = Inviter.start(code.words)
        val invitee = Invitee.respond(InviteCode(code.nameplate, inviteeWords), inviter.hello)
        return Exchange(inviter, invitee, inviter.confirm(invitee.reply))
    }

    @Test
    fun `the invitee gets the payload when both use the same code`() = runTest {
        val inviter = Inviter.start(code.words)
        val invitee = Invitee.respond(InviteCode.parse(code.toString())!!, inviter.hello)
        val confirmation = inviter.confirm(invitee.reply)
        val inviteeConfirmation = invitee.confirm(confirmation)
        assertTrue(inviteeConfirmation.inviterTagValid)

        val received = invitee.open(inviter.finish(inviteeConfirmation.tb, payload))

        assertEquals(payload.groupId, received.groupId)
        assertArrayEquals(payload.groupKey, received.groupKey)
        assertArrayEquals(payload.joinToken, received.joinToken)
    }

    @Test
    fun `the inviter sends nothing when the code is wrong`() = runTest {
        val e = exchange(wrongWords)
        val inviteeConfirmation = e.invitee.confirm(e.confirmation)
        assertAborts { e.inviter.finish(inviteeConfirmation.tb, payload) }
    }

    @Test
    fun `the invitee notices a wrong code too and still answers with a tag`() = runTest {
        val e = exchange(wrongWords)
        val inviteeConfirmation = e.invitee.confirm(e.confirmation)
        assertFalse(inviteeConfirmation.inviterTagValid)
        assertEquals(32, inviteeConfirmation.tb.size)
    }

    @Test
    fun `an invitee that did not verify the inviter cannot open anything`() = runTest {
        val e = exchange(wrongWords)
        e.invitee.confirm(e.confirmation)
        assertAborts { e.invitee.open(InviterFinal(ByteArray(12), ByteArray(80))) }
    }

    @Test
    fun `a tampered invitee tag is rejected`() = runTest {
        val e = exchange(code.words)
        val tb = e.invitee.confirm(e.confirmation).tb
        assertAborts { e.inviter.finish(tb.flipBit(), payload) }
    }

    @Test
    fun `a tampered inviter tag is noticed by the invitee`() = runTest {
        val e = exchange(code.words)
        val tampered = InviterConfirmation(e.confirmation.round2, e.confirmation.ta.flipBit())
        assertFalse(e.invitee.confirm(tampered).inviterTagValid)
    }

    @Test
    fun `the invitee rejects a tampered payload`() = runTest {
        val e = exchange(code.words)
        val tb = e.invitee.confirm(e.confirmation).tb
        val final = e.inviter.finish(tb, payload)
        assertAborts { e.invitee.open(InviterFinal(final.payloadIv, final.payloadCipherText.flipBit())) }
        assertAborts { e.invitee.open(InviterFinal(final.payloadIv.flipBit(), final.payloadCipherText)) }
    }

    @Test
    fun `the inviter rejects tampered invitee rounds`() = runTest {
        val inviter = Inviter.start(code.words)
        val reply = Invitee.respond(code, inviter.hello).reply
        assertAborts {
            inviter.confirm(InviteeReply(reply.round1.flipMiddleBit(), reply.round2))
        }
    }

    @Test
    fun `the invitee rejects tampered inviter rounds`() = runTest {
        val inviter = Inviter.start(code.words)
        val invitee = Invitee.respond(code, inviter.hello)
        val confirmation = inviter.confirm(invitee.reply)
        assertAborts {
            invitee.confirm(InviterConfirmation(confirmation.round2.flipMiddleBit(), confirmation.ta))
        }
    }

    @Test
    fun `malformed messages are rejected`() = runTest {
        val inviter = Inviter.start(code.words)
        assertAborts { Invitee.respond(code, InviterHello(inviter.hello.sid.copyOf(15), inviter.hello.round1)) }
        assertAborts { Invitee.respond(code, InviterHello(inviter.hello.sid, inviter.hello.round1.copyOf(40))) }
        assertAborts { Invitee.respond(code, InviterHello(inviter.hello.sid, ByteArray(0))) }
        val reply = Invitee.respond(code, inviter.hello).reply
        assertAborts { inviter.confirm(InviteeReply(reply.round1, ByteArray(0))) }
    }

    @Test
    fun `every invite uses a fresh session id`() = runTest {
        assertFalse(Inviter.start(code.words).hello.sid.contentEquals(Inviter.start(code.words).hello.sid))
    }
}
