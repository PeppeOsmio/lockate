package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupStatusTest {
    private val date = LocalDateTime(2026, 1, 1, 0, 0, 0)

    private fun group(sharing: Boolean = true, isMember: Boolean = true, exists: Boolean = true) =
        AnonymousGroup(
            internalId = 1,
            id = "g",
            name = "Family",
            createdAt = date,
            joinedAt = date,
            memberName = "me",
            memberId = "me",
            memberToken = ByteArray(0),
            memberIsAGAdmin = false,
            isMember = isMember,
            existsRemote = exists,
            sendLocation = sharing,
            key = ByteArray(0),
            connectionId = 1,
        )

    @Test
    fun `sharing and how many are in it`() {
        assertEquals("Sharing · 3 members", groupStatus(group(), 3))
        assertEquals("Not sharing · only you", groupStatus(group(sharing = false), 1))
        assertEquals("Sharing", groupStatus(group(), null))
    }

    @Test
    fun `a group this phone can't use anymore says why`() {
        assertEquals("Removed", groupStatus(group(isMember = false), 3))
        assertEquals("Deleted", groupStatus(group(exists = false), 3))
    }
}
