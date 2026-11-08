package com.peppeosmio.lockate.data

import com.peppeosmio.lockate.data.anonymous_group.remote.AGFrameDto
import com.peppeosmio.lockate.data.anonymous_group.remote.AGMemberMessageReqDto
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiJsonTest {
    @Test
    fun `fields the app doesn't know are ignored`() {
        val frame =
            apiJson.decodeFromString<AGFrameDto>(
                """{"type":"member.left","memberId":"m1","addedLater":{"x":1}}"""
            )

        assertEquals(AGFrameDto(type = AGFrameDto.MEMBER_LEFT, memberId = "m1"), frame)
    }

    @Test
    fun `a presence frame carries the backend's last seen time`() {
        val frame =
            apiJson.decodeFromString<AGFrameDto>(
                """{"type":"member.presence","memberId":"m1","online":false,"lastSeen":"2026-11-08T12:03:00.000000"}"""
            )

        assertEquals(false, frame.online)
        assertEquals(LocalDateTime(2026, 11, 8, 12, 3), frame.lastSeen)
    }

    @Test
    fun `the pause message is only its type`() {
        assertEquals(
            """{"type":"member.paused"}""",
            apiJson.encodeToString<AGMemberMessageReqDto>(AGMemberMessageReqDto.Paused),
        )
    }
}
