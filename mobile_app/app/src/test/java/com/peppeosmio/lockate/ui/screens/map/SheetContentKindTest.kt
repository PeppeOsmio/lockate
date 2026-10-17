package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsState
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetContentKindTest {

    private val date = LocalDateTime(2026, 1, 1, 0, 0, 0)

    private fun group(isMember: Boolean = true, existsRemote: Boolean = true) = AnonymousGroup(
        internalId = 1,
        id = "group",
        name = "Group",
        createdAt = date,
        joinedAt = date,
        memberName = "me",
        memberId = "me",
        memberToken = ByteArray(0),
        memberIsAGAdmin = false,
        isMember = isMember,
        existsRemote = existsRemote,
        sendLocation = true,
        key = ByteArray(0),
        connectionId = 1
    )

    private fun member(id: String) = AGMember(
        internalId = 1, id = id, name = id, createdAt = date, lastLocationRecord = null, isAGAdmin = false
    )

    private fun mapState(groups: List<AnonymousGroup>?, selected: Long? = 1) =
        MapState(groups = groups, selectedGroupInternalId = selected)

    private fun details(selectedMemberId: String? = null, members: Map<String, AGMember>? = mapOf("me" to member("me"))) =
        AGDetailsState(anonymousGroup = group(), members = members, selectedMemberId = selectedMemberId)

    @Test
    fun `groups still loading`() {
        assertEquals(SheetContentKind.Loading, sheetContentKind(mapState(null), AGDetailsState()))
    }

    @Test
    fun `groups exist but none is selected yet`() {
        assertEquals(SheetContentKind.Loading, sheetContentKind(mapState(listOf(group()), selected = null), AGDetailsState()))
    }

    @Test
    fun `no groups`() {
        assertEquals(SheetContentKind.NoGroups, sheetContentKind(mapState(emptyList()), AGDetailsState()))
    }

    @Test
    fun `group that no longer exists or that you were removed from`() {
        assertEquals(SheetContentKind.BrokenGroup, sheetContentKind(mapState(listOf(group(existsRemote = false))), AGDetailsState()))
        assertEquals(SheetContentKind.BrokenGroup, sheetContentKind(mapState(listOf(group(isMember = false))), AGDetailsState()))
    }

    @Test
    fun `members not loaded yet`() {
        assertEquals(SheetContentKind.Loading, sheetContentKind(mapState(listOf(group())), details(members = null)))
    }

    @Test
    fun `members list when no member is selected`() {
        assertEquals(SheetContentKind.Members, sheetContentKind(mapState(listOf(group())), details()))
    }

    @Test
    fun `member details when a known member is selected`() {
        assertEquals(SheetContentKind.MemberDetails, sheetContentKind(mapState(listOf(group())), details(selectedMemberId = "me")))
    }

    @Test
    fun `selected member that is not in the group falls back to the list`() {
        assertEquals(SheetContentKind.Members, sheetContentKind(mapState(listOf(group())), details(selectedMemberId = "ghost")))
    }
}
