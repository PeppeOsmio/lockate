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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.ui.composables.CircleRow
import com.peppeosmio.lockate.ui.composables.MemberRow
import com.peppeosmio.lockate.ui.composables.SharingRow
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsState
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LastSeenFreshness
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.dotColor
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What the user can do from the sheet, whatever it shows */
class MapSheetActions(
    val onSharingChange: (Boolean) -> Unit,
    val onCreateGroup: () -> Unit,
    val onJoinGroup: () -> Unit,
    val onRemoveGroup: () -> Unit,
    val onInvite: () -> Unit,
    val onOpenCircles: () -> Unit,
    val onShowCircle: (Circle) -> Unit,
    val onSelectMember: (memberId: String) -> Unit,
    val onBackToGroup: () -> Unit,
    val onFollow: () -> Unit,
    val onOpenInMaps: (AGMember) -> Unit,
    val onCopyCoordinates: (AGMember) -> Unit,
    val onCopyMemberId: (AGMember) -> Unit,
    val onMakeAdmin: (memberId: String) -> Unit,
    val onMuteMember: (memberId: String, muted: Boolean) -> Unit,
)

// Keeps the last row clear of the gesture bar, where a tap can trigger the system gesture instead
private val SHEET_BODY_BOTTOM_PADDING = 32.dp

/**
 * The part of the sheet that stays visible when it's collapsed. [sharingSupporting] is the second
 * line of the sharing row, e.g. while the server can't be reached.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun MapSheetHeader(
    content: SheetContent,
    selectedGroup: AnonymousGroup?,
    details: AGDetailsState,
    now: Instant,
    sharingSupporting: String?,
    actions: MapSheetActions,
) {
    val group = details.anonymousGroup
    when (content) {
        SheetContent.Loading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        SheetContent.NoGroups -> NoGroupsHeader(actions)
        SheetContent.GroupGone -> GroupGoneHeader(selectedGroup!!, actions)
        SheetContent.Alone -> {
            SharingRow(
                sharing = group!!.sendLocation,
                onSharingChange = actions.onSharingChange,
                supporting = sharingSupporting,
            )
            AloneState(actions)
        }
        SheetContent.Group ->
            SharingRow(
                sharing = group!!.sendLocation,
                onSharingChange = actions.onSharingChange,
                supporting = sharingSupporting,
            )
        is SheetContent.Member -> {
            val member = details.members!![content.memberId]!!
            MemberHeader(
                member = member,
                isMe = member.id == group!!.memberId,
                status = statusOf(member, details, now),
                freshness = memberFreshness(member, now),
                actions = actions,
            )
        }
        is SheetContent.Following -> {
            val member = details.members!![content.memberId]!!
            FollowingHeader(
                member = member,
                status = statusOf(member, details, now),
                freshness = memberFreshness(member, now),
                onBack = actions.onBackToGroup,
            )
        }
    }
}

/** The part of the sheet revealed by dragging it up, for the contents that have one */
@OptIn(ExperimentalTime::class)
@Composable
fun MapSheetBody(
    content: SheetContent,
    details: AGDetailsState,
    now: Instant,
    actions: MapSheetActions,
) {
    val group = details.anonymousGroup!!
    val members = details.members!!
    val scroll = Modifier.verticalScroll(rememberScrollState())
    when (content) {
        SheetContent.Group ->
            Column(scroll.padding(bottom = SHEET_BODY_BOTTOM_PADDING)) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                MembersSection(group, details, now, actions)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                CirclesSection(details, members.values, group.memberId, actions)
            }
        is SheetContent.Member -> {
            val member = members[content.memberId]!!
            MemberBody(
                modifier = scroll.padding(bottom = SHEET_BODY_BOTTOM_PADDING),
                member = member,
                isMe = member.id == group.memberId,
                isMuted = member.id in details.mutedMemberIds,
                canMakeAdmin =
                    group.memberIsAGAdmin && member.id != group.memberId && !member.isAGAdmin,
                now = now,
                actions = actions,
            )
        }
        else -> Unit
    }
}

@OptIn(ExperimentalTime::class)
private fun statusOf(
    member: AGMember,
    details: AGDetailsState,
    now: Instant,
): String =
    memberStatus(
        member = member,
        circles = details.circles,
        now = now,
    )

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        action()
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun MembersSection(
    group: AnonymousGroup,
    details: AGDetailsState,
    now: Instant,
    actions: MapSheetActions,
) {
    SectionHeader("Members") {
        TextButton(onClick = actions.onInvite) {
            Icon(
                painterResource(R.drawable.baseline_person_add_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Invite")
        }
    }
    val members =
        details.members!!
            .values
            .sortedWith(
                compareByDescending<AGMember> { it.id == group.memberId }
                    .thenBy { it.name.lowercase() }
            )
    for (member in members) {
        val isMe = member.id == group.memberId
        val freshness = memberFreshness(member, now)
        MemberRow(
            name = member.name,
            nameSuffix = if (isMe) "You" else null,
            memberId = member.id,
            supporting = statusOf(member, details, now),
            isMe = isMe,
            activity = member.lastLocationRecord?.activity ?: UserActivity.Unknown,
            paused = freshness == LastSeenFreshness.Stale,
            statusColor = freshness.dotColor(),
            onClick = { actions.onSelectMember(member.id) },
            trailing =
                when {
                    member.isAGAdmin -> ({ AdminBadge() })
                    !isMe -> ({
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        })
                    else -> null
                },
        )
    }
}

@Composable
internal fun AdminBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.baseline_security_24),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "Admin",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The circles someone is in, and one row for all the others, which opens Circles */
@Composable
private fun CirclesSection(
    details: AGDetailsState,
    members: Collection<AGMember>,
    myMemberId: String,
    actions: MapSheetActions,
) {
    val circles = details.circles
    SectionHeader(if (circles.isEmpty()) "Circles" else "Circles · ${circles.size}") {
        TextButton(onClick = actions.onOpenCircles) { Text("Manage") }
    }
    val occupied = occupiedCircles(circles, members)
    for ((circle, inside) in occupied.entries.sortedBy { it.key.name.lowercase() }) {
        CircleRow(
            name = circle.name,
            supporting = whoIsHere(inside, myMemberId),
            onClick = { actions.onShowCircle(circle) },
        )
    }
    val others = circles.size - occupied.size
    if (others > 0) {
        CircleRow(
            name = if (others == 1) "1 more circle" else "$others more circles",
            supporting = "No one inside",
            onClick = actions.onOpenCircles,
            trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}

internal fun whoIsHere(inside: List<AGMember>, myMemberId: String): String {
    val names =
        inside
            .sortedWith(compareByDescending<AGMember> { it.id == myMemberId }.thenBy { it.name })
            .map { if (it.id == myMemberId) "You" else it.name }
    return when (names.size) {
        1 -> if (names[0] == "You") "You're here" else "${names[0]} is here"
        2 -> "${names[0]} and ${names[1]} are here"
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} more are here"
    }
}

@Composable
private fun AloneState(actions: MapSheetActions) {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.baseline_person_add_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.size(16.dp))
        Text("You're the only one here", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            "Invite someone with a one-time code. Turn on sharing when you want them to see you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = actions.onInvite) {
            Icon(
                painterResource(R.drawable.baseline_person_add_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Invite someone")
        }
    }
}

@Composable
private fun NoGroupsHeader(actions: MapSheetActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Start a group", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create one and invite the people you want to see, or join with a code someone " +
                "gives you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = actions.onCreateGroup) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Create a group")
        }
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = actions.onJoinGroup) {
            Text("Join with a code")
        }
    }
}

@Composable
private fun GroupGoneHeader(group: AnonymousGroup, actions: MapSheetActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                text = if (group.existsRemote) "Removed" else "Deleted",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text =
                if (group.existsRemote) "You were removed from ${group.name}"
                else "${group.name} was deleted",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text =
                if (group.existsRemote) {
                    "An admin removed you. Nobody in ${group.name} sees you, and you don't see them."
                } else {
                    "An admin deleted it for everyone. Nobody sees anyone in it anymore."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = actions.onRemoveGroup) {
            Text("Remove from this phone")
        }
    }
}
