package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupSelectionTest {

    private fun group(internalId: Long, name: String) = AnonymousGroup(
        internalId = internalId,
        id = "id-$internalId",
        name = name,
        createdAt = LocalDateTime(2026, 1, 1, 0, 0, 0),
        joinedAt = LocalDateTime(2026, 1, 1, 0, 0, 0),
        memberName = "me",
        memberId = "member-$internalId",
        memberToken = ByteArray(0),
        memberIsAGAdmin = false,
        isMember = true,
        existsRemote = true,
        sendLocation = true,
        key = ByteArray(0),
        connectionId = 1
    )

    private val groups = listOf(group(1, "palestra"), group(2, "Famiglia"), group(3, "Amici"))

    @Test
    fun `sortGroups orders by name ignoring case`() {
        assertEquals(listOf("Amici", "Famiglia", "palestra"), sortGroups(groups).map { it.name })
    }

    @Test
    fun `filterGroups matches substrings ignoring case`() {
        assertEquals(listOf(2L), filterGroups(groups, "FAM").map { it.internalId })
        assertEquals(listOf(1L, 2L, 3L), filterGroups(groups, "a").map { it.internalId }.sorted())
    }

    @Test
    fun `filterGroups with blank query returns everything`() {
        assertEquals(groups, filterGroups(groups, "  "))
    }

    @Test
    fun `pickInitialGroup prefers the last used group`() {
        assertEquals(1L, pickInitialGroup(groups, lastInternalId = 1)?.internalId)
    }

    @Test
    fun `pickInitialGroup falls back to the first sorted group when last is missing`() {
        assertEquals(3L, pickInitialGroup(groups, lastInternalId = 99)?.internalId)
        assertEquals(3L, pickInitialGroup(groups, lastInternalId = null)?.internalId)
    }

    @Test
    fun `pickInitialGroup returns null without groups`() {
        assertNull(pickInitialGroup(emptyList(), lastInternalId = 1))
    }
}
