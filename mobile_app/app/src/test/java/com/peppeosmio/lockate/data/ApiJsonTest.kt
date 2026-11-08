package com.peppeosmio.lockate.data

import com.peppeosmio.lockate.data.anonymous_group.remote.AGFrameDto
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
}
