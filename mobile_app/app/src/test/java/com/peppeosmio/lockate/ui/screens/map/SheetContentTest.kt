package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsState
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetContentTest {

    private val date = LocalDateTime(2026, 1, 1, 0, 0, 0)

    private fun group(isMember: Boolean = true, existsRemote: Boolean = true) =
        AnonymousGroup(
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
            connectionId = 1,
        )

    private fun member(id: String) =
        AGMember(
            internalId = 1,
            id = id,
            name = id,
            createdAt = date,
            lastLocationRecord = null,
            isAGAdmin = false,
        )

    private fun mapState(groups: List<AnonymousGroup>?, selected: Long? = 1) =
        MapState(groups = groups, selectedGroupInternalId = selected)

    private val twoMembers = mapOf("me" to member("me"), "marco" to member("marco"))

    private fun details(
        selectedMemberId: String? = null,
        members: Map<String, AGMember>? = twoMembers,
        following: Boolean = false,
    ) =
        AGDetailsState(
            anonymousGroup = group(),
            members = members,
            selectedMemberId = selectedMemberId,
            isFollowingSelectedMember = following,
        )

    @Test
    fun `groups still loading`() {
        assertEquals(SheetContent.Loading, sheetContent(mapState(null), AGDetailsState()))
    }

    @Test
    fun `groups exist but none is selected yet`() {
        assertEquals(
            SheetContent.Loading,
            sheetContent(mapState(listOf(group()), selected = null), AGDetailsState()),
        )
    }

    @Test
    fun `no groups`() {
        assertEquals(
            SheetContent.NoGroups,
            sheetContent(mapState(emptyList()), AGDetailsState()),
        )
    }

    @Test
    fun `group that no longer exists or that you were removed from`() {
        assertEquals(
            SheetContent.GroupGone,
            sheetContent(mapState(listOf(group(existsRemote = false))), AGDetailsState()),
        )
        assertEquals(
            SheetContent.GroupGone,
            sheetContent(mapState(listOf(group(isMember = false))), AGDetailsState()),
        )
    }

    @Test
    fun `members not loaded yet`() {
        assertEquals(
            SheetContent.Loading,
            sheetContent(mapState(listOf(group())), details(members = null)),
        )
    }

    @Test
    fun `group when no member is selected`() {
        assertEquals(
            SheetContent.Group,
            sheetContent(mapState(listOf(group())), details()),
        )
    }

    @Test
    fun `member details when a known member is selected`() {
        assertEquals(
            SheetContent.Member("marco"),
            sheetContent(mapState(listOf(group())), details(selectedMemberId = "marco")),
        )
    }

    @Test
    fun `following when the selected member is followed`() {
        assertEquals(
            SheetContent.Following("marco"),
            sheetContent(
                mapState(listOf(group())),
                details(selectedMemberId = "marco", following = true),
            ),
        )
    }

    @Test
    fun `alone when this phone's member is the only one`() {
        assertEquals(
            SheetContent.Alone,
            sheetContent(mapState(listOf(group())), details(members = mapOf("me" to member("me")))),
        )
    }

    @Test
    fun `selected member that is not in the group falls back to the group`() {
        assertEquals(
            SheetContent.Group,
            sheetContent(mapState(listOf(group())), details(selectedMemberId = "ghost")),
        )
    }
}
