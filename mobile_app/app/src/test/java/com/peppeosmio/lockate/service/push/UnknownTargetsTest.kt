package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownTargetsTest {

    private val at = LocalDateTime(2026, 11, 14, 9, 0)

    private fun event(memberId: String, circleId: String) =
        AGChannelFrame.CircleEvent(
            memberId = memberId,
            transition = CircleTransition(circleId, TransitionKind.Enter),
            timestamp = at,
        )

    @Test
    fun `a circle event of a known member in a known circle names nothing unknown`() {
        assertFalse(namesUnknown(event("m", "c"), members = setOf("m"), circles = setOf("c")))
    }

    @Test
    fun `a circle event of an unknown member names something unknown`() {
        assertTrue(namesUnknown(event("new", "c"), members = setOf("m"), circles = setOf("c")))
    }

    @Test
    fun `a circle event in an unknown circle names something unknown`() {
        assertTrue(namesUnknown(event("m", "new"), members = setOf("m"), circles = setOf("c")))
    }

    @Test
    fun `a sharing change looks only at the member`() {
        val presence = AGChannelFrame.MemberPresence("m", true, null)

        assertFalse(namesUnknown(presence, members = setOf("m"), circles = emptySet()))
        assertTrue(namesUnknown(presence, members = emptySet(), circles = emptySet()))
    }
}
