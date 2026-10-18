package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.composables.SmallCircularProgressIndicator
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsState
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGMembersList
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MemberDetailsBody
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MemberDetailsHeader
import com.peppeosmio.lockate.utils.LoadingState

/** [hasBody]: whether there's more than the header to reveal by dragging the sheet up */
enum class SheetContentKind(val hasBody: Boolean) {
    Loading(false), NoGroups(false), BrokenGroup(false), Members(true), MemberDetails(true)
}

fun sheetContentKind(mapState: MapState, details: AGDetailsState): SheetContentKind {
    val groups = mapState.groups
    val selectedGroup = mapState.selectedGroup
    return when {
        groups == null || (groups.isNotEmpty() && selectedGroup == null) -> SheetContentKind.Loading
        groups.isEmpty() -> SheetContentKind.NoGroups
        !selectedGroup!!.isMember || !selectedGroup.existsRemote -> SheetContentKind.BrokenGroup
        details.members == null || details.anonymousGroup == null -> SheetContentKind.Loading
        details.selectedMemberId?.let { details.members[it] } != null -> SheetContentKind.MemberDetails
        else -> SheetContentKind.Members
    }
}

// Keeps the last row clear of the gesture bar, where a tap can trigger the system gesture instead
private val SHEET_BODY_BOTTOM_PADDING = 32.dp

/** The part of the sheet that stays visible when it's collapsed */
@Composable
fun MembersSheetHeader(
    kind: SheetContentKind,
    mapState: MapState,
    details: AGDetailsState,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onRemoveGroup: () -> Unit,
    onBackToMembers: () -> Unit,
    onToggleFollow: (Boolean) -> Unit
) {
    when (kind) {
        SheetContentKind.Loading -> Loading()
        SheetContentKind.NoGroups -> EmptyGroupsState(onCreate = onCreateGroup, onJoin = onJoinGroup)
        SheetContentKind.BrokenGroup -> BrokenGroupState(group = mapState.selectedGroup!!, onRemove = onRemoveGroup)
        SheetContentKind.MemberDetails -> {
            val selectedMember = details.members!![details.selectedMemberId!!]!!
            MemberDetailsHeader(
                member = selectedMember,
                isMe = selectedMember.id == details.anonymousGroup!!.memberId,
                isFollowing = details.isFollowingSelectedMember,
                onBack = onBackToMembers,
                onToggleFollow = onToggleFollow
            )
        }

        SheetContentKind.Members -> MembersHeader(
            membersCount = details.members!!.size,
            isSharing = details.anonymousGroup!!.sendLocation,
            isLoading = details.remoteDataLoadingState == LoadingState.IsLoading
        )
    }
}

/** The part of the sheet revealed by dragging it up, for the kinds where [SheetContentKind.hasBody] */
@Composable
fun MembersSheetBody(
    kind: SheetContentKind,
    details: AGDetailsState,
    onSelectMember: (memberId: String) -> Unit,
    onCopyCoordinates: (AGMember) -> Unit,
    onOpenInMaps: (AGMember) -> Unit,
    onCopyMemberId: (AGMember) -> Unit,
    onMakeAdmin: (memberId: String) -> Unit
) {
    when (kind) {
        SheetContentKind.MemberDetails -> {
            val group = details.anonymousGroup!!
            val selectedMember = details.members!![details.selectedMemberId!!]!!
            MemberDetailsBody(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = SHEET_BODY_BOTTOM_PADDING),
                member = selectedMember,
                canMakeAdmin = group.memberIsAGAdmin && selectedMember.id != group.memberId && !selectedMember.isAGAdmin,
                onCopyCoordinates = { onCopyCoordinates(selectedMember) },
                onOpenInMaps = { onOpenInMaps(selectedMember) },
                onCopyId = { onCopyMemberId(selectedMember) },
                onMakeAdmin = { onMakeAdmin(selectedMember.id) })
        }

        SheetContentKind.Members -> {
            val group = details.anonymousGroup!!
            AGMembersList(
                modifier = Modifier.padding(bottom = SHEET_BODY_BOTTOM_PADDING),
                authenticatedMemberId = group.memberId,
                members = details.members!!.values.sortedWith(
                    compareByDescending<AGMember> { it.id == group.memberId }.thenBy { it.name.lowercase() }
                ),
                onTapMember = onSelectMember
            )
        }

        else -> Unit
    }
}

@Composable
private fun MembersHeader(membersCount: Int, isSharing: Boolean, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = if (membersCount == 1) "1 member" else "$membersCount members",
            style = MaterialTheme.typography.titleMedium
        )
        if (isLoading) {
            SmallCircularProgressIndicator()
        }
        val color = if (isSharing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            painter = painterResource(R.drawable.outline_location_searching_24),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = if (isSharing) "Sharing your location" else "Not sharing",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp), contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyGroupsState(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = "Create your first group",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = "Share your location with the people you choose. Everything is end-to-end encrypted: the server never sees names or coordinates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(16.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onCreate) { Text("Create group") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onJoin) { Text("Join with ID and password") }
    }
}

@Composable
private fun BrokenGroupState(group: AnonymousGroup, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (!group.existsRemote) "This group no longer exists" else "You were removed from this group",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = "You can remove it from this device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRemove) { Text("Remove from device") }
    }
}
